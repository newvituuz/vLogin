package br.vituz.core.vlogin.bukkit.listener;

import br.vituz.core.vlogin.bukkit.BukkitAuthPlayer;
import br.vituz.core.vlogin.bukkit.VLoginBukkit;
import br.vituz.core.vlogin.common.auth.AuthManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;

public final class ConnectionListener implements Listener {
    private final VLoginBukkit plugin;

    public ConnectionListener(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.core().settings().isBackendBehindProxy()) {
            return;
        }
        String address = event.getAddress() == null ? "127.0.0.1" : event.getAddress().getHostAddress();
        AuthManager.PreLoginResult result = plugin.core().auth().checkPreLogin(event.getName(), address);
        if (!result.allowed) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, result.kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.core().settings().hideJoinMessage
                && !plugin.core().auth().isAuthenticated(player.getName())) {
            event.setJoinMessage(null);
        }

        plugin.limbo().refreshVisibilityFor(player);
        plugin.core().auth().handleJoin(new BukkitAuthPlayer(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        handleDisconnect(event.getPlayer());
    }

    private void handleDisconnect(Player player) {
        if (!plugin.core().auth().isAuthenticated(player.getName())) {
            plugin.limbo().release(player);
        }
        plugin.limbo().forget(player);
        if (plugin.restrictions() != null) {
            plugin.restrictions().forget(player);
        }
        plugin.core().auth().handleQuit(new BukkitAuthPlayer(player));
    }
}
