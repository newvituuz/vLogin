package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.common.command.Command;
import br.vituz.core.vlogin.common.platform.Sender;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Registra os comandos em tempo de execução, já que os nomes vêm do config
 * e por isso não podem ser declarados no plugin.yml.
 */
public final class BukkitCommands {
    private final VLoginBukkit plugin;
    private final List<String> registered = new ArrayList<>();
    private final Set<String> preAuthAliases = new HashSet<>();
    private CommandMap commandMap;

    public BukkitCommands(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    public void register(Command command) {
        CommandMap map = commandMap();
        if (map == null) {
            plugin.getLogger().severe("Could not access the command map; /" + command.name() + " is unavailable.");
            return;
        }
        Adapter adapter = new Adapter(command);
        map.register("vlogin", adapter);
        registered.addAll(command.aliases());

        if (command.allowedBeforeAuth()) {
            for (String alias : command.aliases()) {
                String lower = alias.toLowerCase(Locale.ROOT);
                preAuthAliases.add("/" + lower);
                preAuthAliases.add("/vlogin:" + lower);
            }
        }
    }

    public boolean isAllowedBeforeAuth(String label) {
        return preAuthAliases.contains(label.toLowerCase(Locale.ROOT));
    }

    public void unregisterAll() {
        CommandMap map = commandMap();
        if (map == null) {
            return;
        }
        Map<String, org.bukkit.command.Command> known = knownCommands(map);
        if (known == null) {
            return;
        }
        for (String alias : registered) {
            known.remove(alias.toLowerCase(Locale.ROOT));
            known.remove("vlogin:" + alias.toLowerCase(Locale.ROOT));
        }
        registered.clear();
        preAuthAliases.clear();
    }

    private CommandMap commandMap() {
        if (commandMap != null) {
            return commandMap;
        }
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            commandMap = (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not access the server command map", ex);
        }
        return commandMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, org.bukkit.command.Command> knownCommands(CommandMap map) {
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, org.bukkit.command.Command>) field.get(map);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private final class Adapter extends org.bukkit.command.Command {
        private final Command command;

        Adapter(Command command) {
            super(command.name(),
                    command.description() == null ? "" : command.description(),
                    "/" + command.name(),
                    command.aliases().size() > 1
                            ? command.aliases().subList(1, command.aliases().size())
                            : Collections.<String>emptyList());
            this.command = command;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            command.execute(wrap(sender), args);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return command.suggest(wrap(sender), args);
        }

        private Sender wrap(CommandSender sender) {
            if (sender instanceof Player) {
                return new BukkitAuthPlayer((Player) sender);
            }
            return new BukkitConsole(sender);
        }
    }
}
