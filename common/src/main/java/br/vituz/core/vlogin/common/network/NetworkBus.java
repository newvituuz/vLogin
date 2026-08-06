package br.vituz.core.vlogin.common.network;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * O estado compartilhado entre os nós da rede: presença, sessões e eventos.
 *
 * Com um proxy só, isso é memória local. Com vários, precisa ser Redis,
 * senão cada proxy conhece apenas as próprias sessões.
 */
public interface NetworkBus {
    String nodeId();

    boolean isShared();

    void publish(NetworkMessage.Action action, String player, String extra);

    void subscribe(Consumer<NetworkMessage> handler);

    void markOnline(String player, String address);

    void clearOnline(String player);

    Optional<Presence> presence(String player);

    void openSession(String player, String address, long durationMillis);

    Optional<String> session(String player);

    void clearSession(String player);

    void close();

    final class Presence {
        public final String nodeId;
        public final String address;

        public Presence(String nodeId, String address) {
            this.nodeId = nodeId;
            this.address = address;
        }
    }
}
