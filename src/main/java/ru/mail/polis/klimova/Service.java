package ru.mail.polis.klimova;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import one.nio.http.HttpClient;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.server.AcceptorConfig;
import ru.mail.polis.KVDao;
import ru.mail.polis.KVService;

public class Service extends HttpServer implements KVService {
    private final Set<String> topology;
    private final List<String> topologyList;
    private KVDao dao;
    private String my;
    private Map<String, HttpClient> clientsMap;

    public Service(int port, KVDao dao, Set<String> topology) throws IOException {
        super(createConfig(port));
        this.dao = dao;
        this.topology = topology;
        topologyList = new ArrayList<>(topology);
        my = "http://localhost:" + port;
        clientsMap = new HashMap<>();
        for (String host : topology) {
            if (!host.equals(my)) {
                clientsMap.put(host, new HttpClient(new ConnectionString(host)));
            }
        }
    }

    private static HttpServerConfig createConfig(int port) {
        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[1];
        config.acceptors[0] = new AcceptorConfig();
        config.acceptors[0].port = port;
        return config;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    @Path("/v0/status")
    public Response statusQuery() {
        return Response.ok("OK");
    }

    @Path("/v0/entity")
    public Response clientApiQuery(
            Request request,
            @Param("id=") String idParameter,
            @Param("replicas=") String replicasParameter
    ) {
        if (idParameter == null || idParameter.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] id = idParameter.getBytes();

        Replicas replicas;
        if (replicasParameter == null || replicasParameter.isEmpty()) {
            replicas = Replicas.defaultForCount(topology.size());
        } else {
            try {
                replicas = Replicas.fromQuery(replicasParameter);
            } catch (IllegalArgumentException e) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
        }

        List<String> replicasHosts = topologyList.stream().limit(replicas.getFrom()).collect(Collectors.toList());

        switch (request.getMethod()) {
            case Request.METHOD_GET:
                return processGet(id, replicasHosts, replicas);
            case Request.METHOD_PUT:
                return processPut(id, request.getBody(), replicasHosts, replicas);
            case Request.METHOD_DELETE:
                return processDelete(id, replicasHosts, replicas);
            default:
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
    }

    private Response processGet(byte[] id, List<String> replicasHosts, Replicas replicas) {
        int success = 0;
        int notFound = 0;
        int error = 0;
        int deleted = 0;
        Map<String, byte[]> values = new HashMap<>();
        Set<String> deletedValues = new HashSet<>();
        Map<Long, String> timestamps = new HashMap<>();

        for (String host : replicasHosts) {
            if (host.equals(my)) {
                try {
                    values.put(host, dao.get(id));
                    timestamps.put(dao.getUpdateTimeMillis(id), host);
                    success++;
                } catch (NoSuchElementException e) {
                    try {
                        timestamps.put(dao.getUpdateTimeMillis(id), host);
                        deletedValues.add(host);
                        deleted++;
                    } catch (Exception e1) {
                        notFound++;
                    }
                } catch (IOException e) {
                    error++;
                    e.printStackTrace();
                }
            } else {
                try {
                    Response response = clientsMap.get(host).get(createUriToReplica(id));
                    switch (response.getStatus()) {
                        case 200:
                            values.put(host, response.getBody());
                            timestamps.put(Long.parseLong(response.getHeader("updated: ")), host);
                            success++;
                            break;
                        case 404:
                            String updatedString = response.getHeader("updated: ");
                            if (updatedString == null) {
                                notFound++;
                            } else {
                                timestamps.put(Long.parseLong(updatedString), host);
                                deleted++;
                            }
                            break;
                        default:
                            error++;
                            break;
                    }
                } catch (Exception e) {
                    error++;
                    e.printStackTrace();
                }
            }
        }

        if (values.size() > 0 && deleted == 0) {
            Optional<Map.Entry<String, byte[]>> first = values.entrySet().stream().findFirst();
            if (first.isPresent()) {
                return Response.ok(first.get().getValue());
            } else {
                return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
            }
        } else if (success + deleted + notFound < replicas.getAck()) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        } else {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
    }

    private Response processPut(byte[] id, byte[] value, List<String> replicasHosts, Replicas replicas) {
        int success = 0;
        int error = 0;

        for (String host : replicasHosts) {
            if (host.equals(my)) {
                try {
                    dao.upsert(id, value);
                    success++;
                } catch (IOException e) {
                    error++;
                    e.printStackTrace();
                }
            } else {
                try {
                    Response response = clientsMap.get(host).put(createUriToReplica(id), value);
                    if (response.getStatus() == 201) {
                        success++;
                    } else {
                        error++;
                    }
                } catch (Exception e) {
                    error++;
                    e.printStackTrace();
                }
            }
        }

        if (success >= replicas.getAck()) {
            return new Response(Response.CREATED, Response.EMPTY);
        } else {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
    }

    private Response processDelete(byte[] id, List<String> replicasHosts, Replicas replicas) {
        int success = 0;
        int error = 0;

        for (String host : replicasHosts) {
            if (host.equals(my)) {
                try {
                    dao.remove(id);
                    success++;
                } catch (IOException e) {
                    error++;
                    e.printStackTrace();
                }
            } else {
                try {
                    Response response = clientsMap.get(host).delete(createUriToReplica(id));
                    if (response.getStatus() == 202) {
                        success++;
                    } else {
                        error++;
                    }
                } catch (Exception e) {
                    error++;
                    e.printStackTrace();
                }
            }
        }

        if (success >= replicas.getAck()) {
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } else {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
    }

    private String createUriToReplica(byte[] id) {
        return "/v0/replica?id=" + new String(id);
    }

    @Path("/v0/replica")
    public Response replicaApiQuery(
            Request request,
            @Param("id=") String idParameter
    ) {
        byte[] id = idParameter.getBytes();
        switch (request.getMethod()) {
            case Request.METHOD_GET:
                Response response;
                try {
                    byte[] value = dao.get(id);
                    response = Response.ok(value);
                } catch (NoSuchElementException e) {
                    response = new Response(Response.NOT_FOUND, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
                Long updateTime;
                try {
                    updateTime = dao.getUpdateTimeMillis(id);
                } catch (NoSuchElementException e) {
                    updateTime = null;
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
                if (updateTime != null) {
                    response.addHeader("updated: " + updateTime);
                }
                return response;
            case Request.METHOD_PUT:
                try {
                    dao.upsert(id, request.getBody());
                    return new Response(Response.CREATED, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            case Request.METHOD_DELETE:
                try {
                    dao.remove(id);
                    return new Response(Response.ACCEPTED, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            default:
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
    }
}
