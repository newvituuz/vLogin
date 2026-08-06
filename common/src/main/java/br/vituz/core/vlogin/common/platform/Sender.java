package br.vituz.core.vlogin.common.platform;

public interface Sender {
    String name();

    void sendMessage(String message);

    boolean hasPermission(String permission);

    boolean isPlayer();

    default void sendMessages(Iterable<String> messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }
}
