package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.common.command.Command;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Platform;
import br.vituz.core.vlogin.common.platform.PlatformType;
import br.vituz.core.vlogin.common.platform.Scheduler;
import br.vituz.core.vlogin.common.platform.Sender;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class BukkitPlatform implements Platform {
    private final VLoginBukkit plugin;
    private final BukkitScheduler scheduler;
    private final BukkitCommands commands;

    public BukkitPlatform(VLoginBukkit plugin) {
        this.plugin = plugin;
        this.scheduler = new BukkitScheduler(plugin);
        this.commands = new BukkitCommands(plugin);
    }

    @Override
    public boolean enforcesOnlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public boolean canVerifyMojang() {
        return Bukkit.getOnlineMode() || plugin.premiumLogin() != null;
    }

    @Override
    public boolean isMojangVerified(br.vituz.core.vlogin.common.platform.AuthPlayer player) {
        return plugin.premiumLogin() != null
                && plugin.premiumLogin().isVerified(player.name(), player.uniqueId(), player.address());
    }

    public BukkitCommands commands() {
        return commands;
    }

    @Override
    public PlatformType type() {
        return PlatformType.BUKKIT;
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    @Override
    public File dataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public String serverVersion() {
        return Bukkit.getVersion();
    }

    @Override
    public Collection<? extends AuthPlayer> onlinePlayers() {
        Collection<BukkitAuthPlayer> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(new BukkitAuthPlayer(player));
        }
        return players;
    }

    @Override
    public Optional<? extends AuthPlayer> player(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return player == null ? Optional.<BukkitAuthPlayer>empty() : Optional.of(new BukkitAuthPlayer(player));
    }

    @Override
    public Optional<? extends AuthPlayer> player(UUID uniqueId) {
        Player player = Bukkit.getPlayer(uniqueId);
        return player == null ? Optional.<BukkitAuthPlayer>empty() : Optional.of(new BukkitAuthPlayer(player));
    }

    @Override
    public Sender console() {
        return new BukkitConsole(Bukkit.getConsoleSender());
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public void dispatchPlayerCommand(AuthPlayer player, String command) {
        Player handle = Bukkit.getPlayerExact(player.name());
        if (handle != null) {
            Bukkit.dispatchCommand(handle, command);
        }
    }

    @Override
    public void setRestricted(AuthPlayer player, boolean restricted) {
        Player handle = Bukkit.getPlayerExact(player.name());
        if (handle == null) {
            return;
        }
        if (restricted) {
            plugin.limbo().restrict(handle);
        } else {
            plugin.limbo().release(handle);
        }
    }

    @Override
    public boolean saveLoginSpawn(AuthPlayer player) {
        Player handle = Bukkit.getPlayerExact(player.name());
        return handle != null && plugin.loginSpawn().save(handle.getLocation());
    }

    @Override
    public boolean hasLoginSpawn() {
        return plugin.loginSpawn().isSet();
    }

    @Override
    public boolean sendPluginMessage(AuthPlayer player, String channel, byte[] payload) {
        Player handle = Bukkit.getPlayerExact(player.name());
        if (handle == null) {
            return false;
        }
        try {
            handle.sendPluginMessage(plugin, channel, payload);
            return true;
        } catch (RuntimeException ex) {
            if (!ProxyMessaging.LEGACY_CHANNEL.equals(channel)) {
                try {
                    handle.sendPluginMessage(plugin, ProxyMessaging.LEGACY_CHANNEL, payload);
                    return true;
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
            return false;
        }
    }

    @Override
    public void registerCommand(Command command) {
        commands.register(command);
    }

    @Override
    public void unregisterCommands() {
        commands.unregisterAll();
    }
}
