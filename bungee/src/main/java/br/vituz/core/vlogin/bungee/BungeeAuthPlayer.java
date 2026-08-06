package br.vituz.core.vlogin.bungee;

import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.SoundEffect;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class BungeeAuthPlayer implements AuthPlayer {
    private final ProxiedPlayer player;

    public BungeeAuthPlayer(ProxiedPlayer player) {
        this.player = player;
    }

    public ProxiedPlayer handle() {
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
        return player.isConnected();
    }

    @Override
    public void sendMessage(String message) {
        if (player.isConnected()) {
            player.sendMessage(TextComponent.fromLegacyText(message));
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void kick(String reason) {
        if (player.isConnected()) {
            player.disconnect(TextComponent.fromLegacyText(reason));
        }
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!player.isConnected()) {
            return;
        }
        Title handle = ProxyServer.getInstance().createTitle()
                .title(TextComponent.fromLegacyText(title))
                .subTitle(TextComponent.fromLegacyText(subtitle))
                .fadeIn(fadeIn)
                .stay(stay)
                .fadeOut(fadeOut);
        player.sendTitle(handle);
    }

    @Override
    public void clearTitle() {
        if (player.isConnected()) {
            player.sendTitle(ProxyServer.getInstance().createTitle().clear());
        }
    }

    @Override
    public void sendActionBar(String message) {
        if (player.isConnected()) {
            player.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        }
    }

    @Override
    public void playSound(SoundEffect sound) {
    }

    @Override
    public String locale() {
        return player.getLocale() == null ? null : player.getLocale().toString();
    }

    @Override
    public int protocolVersion() {
        return player.getPendingConnection().getVersion();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BungeeAuthPlayer)) {
            return false;
        }
        return player.getUniqueId().equals(((BungeeAuthPlayer) other).player.getUniqueId());
    }

    @Override
    public int hashCode() {
        return player.getUniqueId().hashCode();
    }
}
