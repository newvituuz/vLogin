package br.vituz.core.vlogin.common.platform;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public interface Platform {
    PlatformType type();

    Logger logger();

    File dataFolder();

    Scheduler scheduler();

    String serverVersion();

    Collection<? extends AuthPlayer> onlinePlayers();

    Optional<? extends AuthPlayer> player(String name);

    Optional<? extends AuthPlayer> player(UUID uniqueId);

    Sender console();

    void dispatchConsoleCommand(String command);

    void dispatchPlayerCommand(AuthPlayer player, String command);

    default void setRestricted(AuthPlayer player, boolean restricted) {
    }

    default boolean enforcesOnlineMode() {
        return false;
    }

    /**
     * Se esta conexão em particular provou a conta na Mojang.
     *
     * Vale para o servidor offline que autentica um jogador de cada vez: o
     * enforcesOnlineMode() é do servidor inteiro e não sabe responder por um só.
     */
    default boolean isMojangVerified(AuthPlayer player) {
        return false;
    }

    /**
     * Se esta plataforma consegue exigir que a conta seja comprovada na Mojang.
     *
     * Responde pelo servidor, não por uma conexão: é o que decide se o modo
     * exclusive tem como proteger um nickname ou se só resta recusá-lo.
     */
    default boolean canVerifyMojang() {
        return enforcesOnlineMode();
    }

    default boolean saveLoginSpawn(AuthPlayer player) {
        return false;
    }

    default boolean hasLoginSpawn() {
        return false;
    }

    boolean sendPluginMessage(AuthPlayer player, String channel, byte[] payload);

    default boolean transfer(AuthPlayer player, String serverName) {
        return false;
    }

    default Collection<String> servers() {
        return java.util.Collections.emptyList();
    }

    default Optional<String> serverOf(AuthPlayer player) {
        return Optional.empty();
    }

    void registerCommand(br.vituz.core.vlogin.common.command.Command command);

    void unregisterCommands();
}
