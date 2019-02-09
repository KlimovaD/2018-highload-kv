package ru.mail.polis.klimova;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Set;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import ru.mail.polis.KVDao;
import ru.mail.polis.KVService;

public class Service extends HttpServer implements KVService {
    private final Set<String> topology;
    private KVDao dao;

    public Service(int port, KVDao dao, Set<String> topology) throws IOException {
        super(createConfig(port));
        this.dao = dao;
        this.topology = topology;
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
            @Param("id=") String idParameter
    ) {
        if (idParameter == null || idParameter.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] key = idParameter.getBytes();

        switch (request.getMethod()) {
            case Request.METHOD_GET:
                try {
                    byte[] value = dao.get(key);
                    return Response.ok(value);
                } catch (NoSuchElementException e) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            case Request.METHOD_PUT:
                try {
                    dao.upsert(key, request.getBody());
                    return new Response(Response.CREATED, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            case Request.METHOD_DELETE:
                try {
                    dao.remove(key);
                    return new Response(Response.ACCEPTED, Response.EMPTY);
                } catch (IOException e) {
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            default:
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
    }
}
