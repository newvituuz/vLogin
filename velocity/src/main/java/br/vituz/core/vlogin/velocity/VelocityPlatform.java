package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import br.vituz.core.vlogin.common.command.Command;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Platform;
import br.vituz.core.vlogin.common.platform.PlatformType;
import br.vituz.core.vlogin.common.platform.Scheduler;
import br.vituz.core.vlogin.common.platform.Sender;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class VelocityPlatform implements Platform {
    static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(ProxyMessaging.CHANNEL);

    private final VLoginVelocity plugin;
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Logger logger;
    private final VelocityScheduler scheduler;
    private final List<String> registeredCommands = new ArrayList<>();
    private final List<String> preAuthAliases = new ArrayList<>();

    public VelocityPlatform(VLoginVelocity plugin, ProxyServer server, Path dataDirectory) {
        this.plugin = plugin;
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.logger = VelocityConsoleLogger.create(server);
        this.scheduler = new VelocityScheduler(plugin, server);
    }

    public ProxyServer server() {
        return server;
    }

    @Override
    public boolean enforcesOnlineMode() {
        return true;
    }

    @Override
    public PlatformType type() {
        return PlatformType.VELOCITY;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public File dataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public String serverVersion() {
        return server.getVersion().getName() + " " + server.getVersion().getVersion();
    }

    @Override
    public Collection<? extends AuthPlayer> onlinePlayers() {
        List<VelocityAuthPlayer> players = new ArrayList<>();
        for (Player player : server.getAllPlayers()) {
            players.add(new VelocityAuthPlayer(player));
        }
        return players;
    }

    @Override
    public Optional<? extends AuthPlayer> player(String name) {
        return server.getPlayer(name).map(VelocityAuthPlayer::new);
    }

    @Override
    public Optional<? extends AuthPlayer> player(UUID uniqueId) {
        return server.getPlayer(uniqueId).map(VelocityAuthPlayer::new);
    }

    @Override
    public Sender console() {
        return new VelocitySender(server.getConsoleCommandSource());
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        ConsoleCommandSource console = server.getConsoleCommandSource();
        server.getCommandManager().executeAsync(console, command);
    }

    @Override
    public void dispatchPlayerCommand(AuthPlayer player, String command) {
        server.getPlayer(player.name()).ifPresent(handle ->
                server.getCommandManager().executeAsync(handle, command));
    }

    @Override
    public boolean sendPluginMessage(AuthPlayer player, String channel, byte[] payload) {
        Optional<Player> handle = server.getPlayer(player.name());
        if (!handle.isPresent()) {
            return false;
        }
        Optional<ServerConnection> connection = handle.get().getCurrentServer();
        return connection.isPresent() && connection.get().sendPluginMessage(CHANNEL, payload);
    }

    @Override
    public boolean transfer(AuthPlayer player, String serverName) {
        Optional<Player> handle = server.getPlayer(player.name());
        Optional<RegisteredServer> target = server.getServer(serverName);
        if (!handle.isPresent()) {
            return false;
        }
        if (!target.isPresent()) {
            logger.warning("Unknown server '" + serverName + "' in the redirect configuration.");
            return false;
        }
        handle.get().createConnectionRequest(target.get()).fireAndForget();
        return true;
    }

    @Override
    public Collection<String> servers() {
        List<String> names = new ArrayList<>();
        for (RegisteredServer registered : server.getAllServers()) {
            names.add(registered.getServerInfo().getName());
        }
        return names;
    }

    @Override
    public Optional<String> serverOf(AuthPlayer player) {
        return server.getPlayer(player.name())
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.getServerInfo().getName());
    }

    @Override
    public void registerCommand(Command command) {
        List<String> aliases = command.aliases();
        CommandMeta.Builder builder = server.getCommandManager().metaBuilder(aliases.get(0));
        if (aliases.size() > 1) {
            builder.aliases(aliases.subList(1, aliases.size()).toArray(new String[0]));
        }
        server.getCommandManager().register(builder.plugin(plugin).build(), new Adapter(command));
        registeredCommands.addAll(aliases);

        if (command.allowedBeforeAuth()) {
            for (String alias : aliases) {
                preAuthAliases.add(alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    public void unregisterCommands() {
        for (String alias : registeredCommands) {
            server.getCommandManager().unregister(alias);
        }
        registeredCommands.clear();
        preAuthAliases.clear();
    }

    public boolean isAllowedBeforeAuth(String label) {
        return preAuthAliases.contains(label.toLowerCase(Locale.ROOT));
    }

    private static final class Adapter implements SimpleCommand {
        private final Command command;

        Adapter(Command command) {
            this.command = command;
        }

        @Override
        public void execute(Invocation invocation) {
            command.execute(wrap(invocation.source()), invocation.arguments());
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return command.suggest(wrap(invocation.source()), invocation.arguments());
        }

        private static Sender wrap(CommandSource source) {
            if (source instanceof Player) {
                return new VelocityAuthPlayer((Player) source);
            }
            return new VelocitySender(source);
        }
    }

    static final class VelocitySender implements Sender {
        private final CommandSource source;

        VelocitySender(CommandSource source) {
            this.source = source;
        }

        @Override
        public String name() {
            return "CONSOLE";
        }

        @Override
        public void sendMessage(String message) {
            source.sendMessage(VelocityAuthPlayer.text(message));
        }

        @Override
        public boolean hasPermission(String permission) {
            return source.hasPermission(permission);
        }

        @Override
        public boolean isPlayer() {
            return false;
        }
    }
}
