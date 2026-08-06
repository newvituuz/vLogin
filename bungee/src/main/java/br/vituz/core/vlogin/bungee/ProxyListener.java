package br.vituz.core.vlogin.bungee;

import br.vituz.core.vlogin.common.auth.AuthManager;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * O fluxo de autenticação no proxy: pré-login, entrada, troca de servidor
 * e as mensagens vindas dos backends.
 */
public final class ProxyListener implements Listener {
    private final VLoginBungee plugin;

    public ProxyListener(VLoginBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PendingConnection connection = event.getConnection();
        String name = connection.getName();
        if (name == null) {
            return;
        }

        event.registerIntent(plugin);
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try {
                AuthManager.PreLoginResult result =
                        plugin.core().auth().checkPreLogin(name, addressOf(connection.getSocketAddress()));
                if (!result.allowed) {
                    event.setCancelled(true);
                    event.setCancelReason(TextComponent.fromLegacyText(result.kickMessage));
                    return;
                }
                if (result.forceOnlineMode) {
                    connection.setOnlineMode(true);
                }
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        plugin.core().auth().handleJoin(new BungeeAuthPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        plugin.core().auth().handleQuit(new BungeeAuthPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerConnect(ServerConnectEvent event) {
        if (event.isCancelled()) {
            return;
        }
        List<String> authServers = plugin.core().settings().authServers;
        if (authServers.isEmpty() || !plugin.core().settings().firstServerLock) {
            return;
        }
        ProxiedPlayer player = event.getPlayer();
        if (plugin.core().auth().isAuthenticated(player.getName())) {
            return;
        }
        if (authServers.contains(event.getTarget().getName())) {
            return;
        }

        ServerInfo target = ProxyServer.getInstance().getServerInfo(authServers.get(0));
        if (target == null) {
            plugin.getLogger().warning("Unknown authentication server '" + authServers.get(0) + "'.");
            return;
        }
        event.setTarget(target);
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        plugin.core().auth().announceAuthentication(new BungeeAuthPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer)) {
            return;
        }
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (plugin.core().auth().isAuthenticated(player.getName())
                || plugin.core().auth().isUnrestricted(player.getName())) {
            return;
        }

        String message = event.getMessage();
        if (!event.isCommand()) {
            event.setCancelled(true);
            player.sendMessage(TextComponent.fromLegacyText(
                    plugin.core().messages().get(MessageKey.BLOCKED_CHAT)));
            return;
        }

        int space = message.indexOf(' ');
        String label = (space == -1 ? message : message.substring(0, space)).toLowerCase(Locale.ROOT);
        if (plugin.platform().isAllowedBeforeAuth(label)
                || plugin.core().settings().allowedBeforeLogin.contains(label)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(TextComponent.fromLegacyText(
                plugin.core().messages().get(MessageKey.BLOCKED_COMMAND)));
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        String tag = event.getTag();
        if (!ProxyMessaging.CHANNEL.equals(tag) && !ProxyMessaging.LEGACY_CHANNEL.equals(tag)) {
            return;
        }
        if (event.getSender() instanceof ProxiedPlayer) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getReceiver() instanceof ProxiedPlayer)) {
            return;
        }
        event.setCancelled(true);

        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
        Optional<ProxyMessaging.Message> decoded = plugin.core().proxyMessaging().decode(event.getData());
        if (!decoded.isPresent()) {
            return;
        }
        ProxyMessaging.Message message = decoded.get();
        if (!message.player.equalsIgnoreCase(player.getName())) {
            return;
        }

        switch (message.action) {
            case LOGIN:
                plugin.core().auth().applyProxyAuthentication(message.player);
                break;
            case LOGOUT:
                plugin.core().auth().session(message.player)
                        .ifPresent(session -> session.authenticated(false));
                break;
            case COMMAND:
                if (!plugin.core().settings().allowBackendCommands) {
                    plugin.getLogger().warning("Um backend pediu a execução de um comando no"
                            + " proxy, mas network.allow-backend-commands está desligado.");
                    break;
                }
                ProxyServer.getInstance().getPluginManager().dispatchCommand(
                        ProxyServer.getInstance().getConsole(),
                        message.extra.replace("@player", message.player));
                break;
            default:
                break;
        }
    }

    private static String addressOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) address;
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress();
            }
        }
        return "127.0.0.1";
    }
}
