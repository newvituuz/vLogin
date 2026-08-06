package br.vituz.core.vlogin.common.platform;

import java.util.UUID;

public interface AuthPlayer extends Sender {
    UUID uniqueId();

    String address();

    boolean isOnline();

    void kick(String reason);

    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void clearTitle();

    void sendActionBar(String message);

    void playSound(SoundEffect sound);

    String locale();

    int protocolVersion();

    @Override
    default boolean isPlayer() {
        return true;
    }
}
