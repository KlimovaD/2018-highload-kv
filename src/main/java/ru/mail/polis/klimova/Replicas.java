package ru.mail.polis.klimova;

public class Replicas {
    private int ack;
    private int from;

    private Replicas(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }

    public static Replicas fromQuery(String query) throws IllegalArgumentException {
        try {
            String[] split = query.split("/");
            return new Replicas(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    public static Replicas defaultForCount(int count) {
        return new Replicas(count / 2 + 1, count);
    }
}
