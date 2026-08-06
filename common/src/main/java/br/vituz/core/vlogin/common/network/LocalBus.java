package br.vituz.core.vlogin.common.network;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * O barramento de um nó só: tudo em memória, nada é publicado.
 */
public final class LocalBus implements NetworkBus {
    private final String nodeId;
    private final Map<String, Presence> online = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public LocalBus(String nodeId) {
        this.nodeId = nodeId;
    }

    private static final class Session {
        final String address;
        final long expiresAt;

        Session(String address, long expiresAt) {
            this.address = address;
            this.expiresAt = expiresAt;
        }
    }

    private static String key(String player) {
        return player.toLowerCase(Locale.ROOT);
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    @Override
    public boolean isShared() {
        return false;
    }

    @Override
    public void publish(NetworkMessage.Action action, String player, String extra) {
    }

    @Override
    public void subscribe(Consumer<NetworkMessage> handler) {
    }

    @Override
    public void markOnline(String player, String address) {
        online.put(key(player), new Presence(nodeId, address));
    }

    @Override
    public void clearOnline(String player) {
        online.remove(key(player));
    }

    @Override
    public Optional<Presence> presence(String player) {
        return Optional.ofNullable(online.get(key(player)));
    }

    @Override
    public void openSession(String player, String address, long durationMillis) {
        if (durationMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        // Sessão só era descartada quando alguém perguntava por ela. Quem não voltasse
        // deixava a entrada parada para sempre.
        sessions.values().removeIf(session -> session.expiresAt <= now);
        sessions.put(key(player), new Session(address, now + durationMillis));
    }

    @Override
    public Optional<String> session(String player) {
        Session session = sessions.get(key(player));
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt <= System.currentTimeMillis()) {
            sessions.remove(key(player));
            return Optional.empty();
        }
        return Optional.of(session.address);
    }

    @Override
    public void clearSession(String player) {
        sessions.remove(key(player));
    }

    @Override
    public void close() {
        online.clear();
        sessions.clear();
    }
}
