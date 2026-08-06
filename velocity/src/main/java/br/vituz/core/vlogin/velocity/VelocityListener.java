package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import br.vituz.core.vlogin.common.auth.AuthManager;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * O fluxo de autenticação no proxy: pré-login, entrada, troca de servidor
 * e as mensagens vindas dos backends.
 */
public final class VelocityListener {
    private final VLoginVelocity plugin;

    public VelocityListener(VLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String name = event.getUsername();
            InetSocketAddress remote = event.getConnection().getRemoteAddress();
            String address = remote == null || remote.getAddress() == null
                    ? "127.0.0.1"
                    : remote.getAddress().getHostAddress();

            AuthManager.PreLoginResult result = plugin.core().auth().checkPreLogin(name, address);
            if (!result.allowed) {
                event.setResult(PreLoginEvent.PreLoginComponentResult
                        .denied(VelocityAuthPlayer.text(result.kickMessage)));
                return;
            }
            if (result.forceOnlineMode) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
            }
        });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        plugin.core().auth().handleJoin(new VelocityAuthPlayer(event.getPlayer()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        plugin.core().auth().handleQuit(new VelocityAuthPlayer(event.getPlayer()));
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        List<String> authServers = plugin.core().settings().authServers;
        if (authServers.isEmpty() || !plugin.core().settings().firstServerLock) {
            return;
        }
        if (plugin.core().auth().isAuthenticated(event.getPlayer().getUsername())) {
            return;
        }
        Optional<RegisteredServer> target = plugin.platform().server().getServer(authServers.get(0));
        if (target.isPresent()) {
            event.setInitialServer(target.get());
        } else {
            plugin.core().logger().warning("Unknown authentication server '" + authServers.get(0) + "'.");
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        List<String> authServers = plugin.core().settings().authServers;
        if (authServers.isEmpty() || !plugin.core().settings().firstServerLock) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.core().auth().isAuthenticated(player.getUsername())
                || plugin.core().auth().isUnrestricted(player.getUsername())) {
            return;
        }
        String target = event.getOriginalServer().getServerInfo().getName();
        if (!authServers.contains(target)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        plugin.core().auth().announceAuthentication(new VelocityAuthPlayer(event.getPlayer()));
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.core().auth().isAuthenticated(player.getUsername())
                || plugin.core().auth().isUnrestricted(player.getUsername())) {
            return;
        }
        event.setResult(PlayerChatEvent.ChatResult.denied());
        player.sendMessage(VelocityAuthPlayer.text(
                plugin.core().messages().get(MessageKey.BLOCKED_CHAT)));
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getCommandSource();
        if (plugin.core().auth().isAuthenticated(player.getUsername())
                || plugin.core().auth().isUnrestricted(player.getUsername())) {
            return;
        }

        String command = event.getCommand();
        int space = command.indexOf(' ');
        String label = (space == -1 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);

        if (plugin.platform().isAllowedBeforeAuth(label)
                || plugin.core().settings().allowedBeforeLogin.contains("/" + label)) {
            return;
        }
        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(VelocityAuthPlayer.text(
                plugin.core().messages().get(MessageKey.BLOCKED_COMMAND)));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!VelocityPlatform.CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        Player player = ((ServerConnection) event.getSource()).getPlayer();

        Optional<ProxyMessaging.Message> decoded = plugin.core().proxyMessaging().decode(event.getData());
        if (!decoded.isPresent()) {
            return;
        }
        ProxyMessaging.Message message = decoded.get();
        if (!message.player.equalsIgnoreCase(player.getUsername())) {
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
                    plugin.core().logger().warning("Um backend pediu a execução de um comando no"
                            + " proxy, mas network.allow-backend-commands está desligado.");
                    break;
                }
                plugin.platform().dispatchConsoleCommand(
                        message.extra.replace("@player", message.player));
                break;
            default:
                break;
        }
    }
}
