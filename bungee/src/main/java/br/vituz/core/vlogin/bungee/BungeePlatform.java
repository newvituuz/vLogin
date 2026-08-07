package br.vituz.core.vlogin.bungee;

import br.vituz.core.vlogin.common.command.Command;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Platform;
import br.vituz.core.vlogin.common.platform.PlatformType;
import br.vituz.core.vlogin.common.platform.Scheduler;
import br.vituz.core.vlogin.common.platform.Sender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class BungeePlatform implements Platform {
    private final VLoginBungee plugin;
    private final BungeeScheduler scheduler;
    private final List<net.md_5.bungee.api.plugin.Command> registered = new ArrayList<>();
    private final List<String> preAuthAliases = new ArrayList<>();

    public BungeePlatform(VLoginBungee plugin) {
        this.plugin = plugin;
        this.scheduler = new BungeeScheduler(plugin);
    }

    /**
     * Só quando o proxy inteiro está em online mode.
     *
     * Dizer "sim" fixo aqui marcava TODA conexão como comprovada na Mojang, inclusive
     * a de quem nunca passou por autenticação nenhuma. Poder exigir a comprovação de
     * uma conexão (o que o proxy realmente sabe fazer) é outra pergunta, respondida
     * em canVerifyMojang.
     */
    @Override
    public boolean enforcesOnlineMode() {
        return ProxyServer.getInstance().getConfig().isOnlineMode();
    }

    @Override
    public boolean canVerifyMojang() {
        return true;
    }

    @Override
    public PlatformType type() {
        return PlatformType.BUNGEE;
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
        return ProxyServer.getInstance().getVersion();
    }

    @Override
    public Collection<? extends AuthPlayer> onlinePlayers() {
        List<BungeeAuthPlayer> players = new ArrayList<>();
        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            players.add(new BungeeAuthPlayer(player));
        }
        return players;
    }

    @Override
    public Optional<? extends AuthPlayer> player(String name) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(name);
        return player == null ? Optional.<BungeeAuthPlayer>empty() : Optional.of(new BungeeAuthPlayer(player));
    }

    @Override
    public Optional<? extends AuthPlayer> player(UUID uniqueId) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uniqueId);
        return player == null ? Optional.<BungeeAuthPlayer>empty() : Optional.of(new BungeeAuthPlayer(player));
    }

    @Override
    public Sender console() {
        return new BungeeSender(ProxyServer.getInstance().getConsole());
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        ProxyServer.getInstance().getPluginManager()
                .dispatchCommand(ProxyServer.getInstance().getConsole(), command);
    }

    @Override
    public void dispatchPlayerCommand(AuthPlayer player, String command) {
        ProxiedPlayer handle = ProxyServer.getInstance().getPlayer(player.name());
        if (handle != null) {
            ProxyServer.getInstance().getPluginManager().dispatchCommand(handle, command);
        }
    }

    @Override
    public boolean sendPluginMessage(AuthPlayer player, String channel, byte[] payload) {
        ProxiedPlayer handle = ProxyServer.getInstance().getPlayer(player.name());
        if (handle == null) {
            return false;
        }
        Server server = handle.getServer();
        if (server == null) {
            return false;
        }
        server.sendData(channel, payload);
        return true;
    }

    @Override
    public boolean transfer(AuthPlayer player, String serverName) {
        ProxiedPlayer handle = ProxyServer.getInstance().getPlayer(player.name());
        if (handle == null) {
            return false;
        }
        ServerInfo target = ProxyServer.getInstance().getServerInfo(serverName);
        if (target == null) {
            plugin.getLogger().warning("Unknown server '" + serverName + "' in the redirect configuration.");
            return false;
        }
        handle.connect(target);
        return true;
    }

    @Override
    public Collection<String> servers() {
        return ProxyServer.getInstance().getServers().keySet();
    }

    @Override
    public Optional<String> serverOf(AuthPlayer player) {
        ProxiedPlayer handle = ProxyServer.getInstance().getPlayer(player.name());
        if (handle == null || handle.getServer() == null) {
            return Optional.empty();
        }
        return Optional.of(handle.getServer().getInfo().getName());
    }

    @Override
    public void registerCommand(Command command) {
        Adapter adapter = new Adapter(command);
        ProxyServer.getInstance().getPluginManager().registerCommand(plugin, adapter);
        registered.add(adapter);
        if (command.allowedBeforeAuth()) {
            for (String alias : command.aliases()) {
                preAuthAliases.add("/" + alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    public void unregisterCommands() {
        for (net.md_5.bungee.api.plugin.Command command : registered) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(command);
        }
        registered.clear();
        preAuthAliases.clear();
    }

    public boolean isAllowedBeforeAuth(String label) {
        return preAuthAliases.contains(label.toLowerCase(Locale.ROOT));
    }

    private final class Adapter extends net.md_5.bungee.api.plugin.Command
            implements net.md_5.bungee.api.plugin.TabExecutor {
        private final Command command;

        Adapter(Command command) {
            super(command.name(), null, aliasArray(command));
            this.command = command;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            command.execute(wrap(sender), args);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            return command.suggest(wrap(sender), args);
        }

        private Sender wrap(CommandSender sender) {
            if (sender instanceof ProxiedPlayer) {
                return new BungeeAuthPlayer((ProxiedPlayer) sender);
            }
            return new BungeeSender(sender);
        }
    }

    private static String[] aliasArray(Command command) {
        List<String> aliases = command.aliases();
        if (aliases.size() <= 1) {
            return new String[0];
        }
        return aliases.subList(1, aliases.size()).toArray(new String[0]);
    }

    static final class BungeeSender implements Sender {
        private final CommandSender sender;

        BungeeSender(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public String name() {
            return sender.getName();
        }

        @Override
        public void sendMessage(String message) {
            sender.sendMessage(TextComponent.fromLegacyText(message));
        }

        @Override
        public boolean hasPermission(String permission) {
            return sender.hasPermission(permission);
        }

        @Override
        public boolean isPlayer() {
            return false;
        }
    }
}
