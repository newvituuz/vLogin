package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.bukkit.compat.FoliaSupport;
import br.vituz.core.vlogin.bukkit.compat.PlayerCompat;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.SoundEffect;
import br.vituz.core.vlogin.common.util.Text;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Um jogador conectado.
 *
 * Todo envio confere se ele ainda está online e é entregue na thread dona
 * dele: boa parte do núcleo roda fora dela, e escrever de lá é o que gera
 * os avisos de pacote não entregue.
 */
public final class BukkitAuthPlayer implements AuthPlayer {
    private static final Method GET_LOCALE = localeMethod();

    private final Player player;

    public BukkitAuthPlayer(Player player) {
        this.player = player;
    }

    public Player handle() {
        return player;
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public UUID uniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String address() {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "127.0.0.1";
        }
        return address.getAddress().getHostAddress();
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    private void onPlayerThread(Runnable action) {
        if (!player.isOnline()) {
            return;
        }
        if (!FoliaSupport.isFolia()) {
            action.run();
            return;
        }
        FoliaSupport.runForPlayer(VLoginBukkit.instance(), player, () -> {
            if (player.isOnline()) {
                action.run();
            }
        });
    }

    @Override
    public void sendMessage(String message) {
        onPlayerThread(() -> player.sendMessage(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void kick(String reason) {
        if (!player.isOnline()) {
            return;
        }
        FoliaSupport.runForPlayer(VLoginBukkit.instance(), player, () -> player.kickPlayer(reason));
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        onPlayerThread(() -> PlayerCompat.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut));
    }

    @Override
    public void clearTitle() {
        onPlayerThread(() -> PlayerCompat.clearTitle(player));
    }

    @Override
    public void sendActionBar(String message) {
        onPlayerThread(() -> PlayerCompat.sendActionBar(player, message));
    }

    @Override
    public void playSound(SoundEffect sound) {
        onPlayerThread(() -> PlayerCompat.playSound(player, sound));
    }

    @Override
    public String locale() {
        if (GET_LOCALE == null) {
            return null;
        }
        try {
            Object receiver = GET_LOCALE.getDeclaringClass() == Player.class ? player : player.spigot();
            Object locale = GET_LOCALE.invoke(receiver);
            return locale == null ? null : Text.stripColors(locale.toString());
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    @Override
    public int protocolVersion() {
        return -1;
    }

    private static Method localeMethod() {
        try {
            return Player.class.getMethod("getLocale");
        } catch (NoSuchMethodException ex) {
            try {
                return Class.forName("org.bukkit.entity.Player$Spigot").getMethod("getLocale");
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BukkitAuthPlayer)) {
            return false;
        }
        return player.getUniqueId().equals(((BukkitAuthPlayer) other).player.getUniqueId());
    }

    @Override
    public int hashCode() {
        return player.getUniqueId().hashCode();
    }
}
