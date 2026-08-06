package br.vituz.core.vlogin.bukkit.listener;

import br.vituz.core.vlogin.bukkit.VLoginBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;

public final class PickupListener implements Listener {
    private final VLoginBukkit plugin;

    public PickupListener(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!bind("org.bukkit.event.entity.EntityPickupItemEvent")) {
            bind("org.bukkit.event.player.PlayerPickupItemEvent");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean bind(String className) {
        Class<? extends Event> eventClass;
        try {
            eventClass = (Class<? extends Event>) Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return false;
        }
        Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.LOWEST,
                (listener, event) -> handle(event), plugin, true);
        return true;
    }

    private void handle(Event event) {
        if (!(event instanceof Cancellable)) {
            return;
        }
        Player player = resolvePlayer(event);
        if (player == null) {
            return;
        }
        if (!plugin.core().auth().isAuthenticated(player.getName())
                && !plugin.core().auth().isUnrestricted(player.getName())) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    private Player resolvePlayer(Event event) {
        for (String getter : new String[]{"getEntity", "getPlayer"}) {
            try {
                Method method = event.getClass().getMethod(getter);
                Object result = method.invoke(event);
                if (result instanceof Player) {
                    return (Player) result;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
