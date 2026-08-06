package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.proxy.Player;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;

public final class VelocityAuthPlayer implements AuthPlayer {
    private final Player player;

    public VelocityAuthPlayer(Player player) {
        this.player = player;
    }

    public Player handle() {
        return player;
    }

    public static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    @Override
    public String name() {
        return player.getUsername();
    }

    @Override
    public UUID uniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String address() {
        InetSocketAddress address = player.getRemoteAddress();
        if (address == null || address.getAddress() == null) {
            return "127.0.0.1";
        }
        return address.getAddress().getHostAddress();
    }

    @Override
    public boolean isOnline() {
        return player.isActive();
    }

    @Override
    public void sendMessage(String message) {
        if (player.isActive()) {
            player.sendMessage(text(message));
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void kick(String reason) {
        if (player.isActive()) {
            player.disconnect(text(reason));
        }
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!player.isActive()) {
            return;
        }
        player.showTitle(Title.title(text(title), text(subtitle),
                Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut))));
    }

    @Override
    public void clearTitle() {
        if (player.isActive()) {
            player.clearTitle();
        }
    }

    @Override
    public void sendActionBar(String message) {
        if (player.isActive()) {
            player.sendActionBar(text(message));
        }
    }

    @Override
    public void playSound(SoundEffect sound) {
    }

    @Override
    public String locale() {
        return player.getEffectiveLocale() == null ? null : player.getEffectiveLocale().toString();
    }

    @Override
    public int protocolVersion() {
        return player.getProtocolVersion().getProtocol();
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0L, ticks * 50L));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VelocityAuthPlayer)) {
            return false;
        }
        return player.getUniqueId().equals(((VelocityAuthPlayer) other).player.getUniqueId());
    }

    @Override
    public int hashCode() {
        return player.getUniqueId().hashCode();
    }
}
