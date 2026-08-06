package br.vituz.core.vlogin.bukkit.listener;

import br.vituz.core.vlogin.bukkit.VLoginBukkit;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Optional;

/**
 * Recebe as decisões assinadas que o proxy toma sobre um jogador.
 */
public final class ProxyMessageListener implements PluginMessageListener {
    private final VLoginBukkit plugin;

    public ProxyMessageListener(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProxyMessaging.CHANNEL.equals(channel) && !ProxyMessaging.LEGACY_CHANNEL.equals(channel)) {
            return;
        }
        Optional<ProxyMessaging.Message> decoded = plugin.core().proxyMessaging().decode(message);
        if (!decoded.isPresent()) {
            return;
        }

        ProxyMessaging.Message parsed = decoded.get();
        if (!parsed.player.equalsIgnoreCase(player.getName())) {
            return;
        }

        switch (parsed.action) {
            case AUTHENTICATED:
                plugin.core().auth().applyProxyAuthentication(parsed.player);
                break;
            case UNAUTHENTICATED:
                break;
            default:
                break;
        }
    }
}
