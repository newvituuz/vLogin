package br.vituz.core.vlogin.bukkit.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Os schedulers regionais do Folia, alcançados por reflexão.
 *
 * Em Folia não existe uma main thread e cada jogador pertence à thread que
 * ticka a região dele. Em servidor comum, tudo cai no scheduler do Bukkit.
 */
public final class FoliaSupport {
    private static final boolean FOLIA;

    private static Method getAsyncScheduler;
    private static Method asyncRunNow;
    private static Method asyncRunAtFixedRate;

    private static Method getGlobalRegionScheduler;
    private static Method globalExecute;
    private static Method globalRunDelayed;

    private static Method getEntityScheduler;
    private static Method entityExecute;
    private static Method entityRunDelayed;

    private static Method cancelTask;
    private static Method teleportAsync;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        FOLIA = folia;
        if (folia) {
            bind();
        }
        bindTeleportAsync();
    }

    private FoliaSupport() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static void bind() {
        try {
            getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            asyncRunNow = asyncScheduler.getMethod("runNow", Plugin.class, Consumer.class);
            asyncRunAtFixedRate = asyncScheduler.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    long.class, long.class, TimeUnit.class);

            getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            globalExecute = globalScheduler.getMethod("execute", Plugin.class, Runnable.class);
            globalRunDelayed = globalScheduler.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

            getEntityScheduler = Class.forName("org.bukkit.entity.Entity").getMethod("getScheduler");
            Class<?> entityScheduler =
                    Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityExecute = entityScheduler.getMethod("execute", Plugin.class, Runnable.class,
                    Runnable.class, long.class);
            entityRunDelayed = entityScheduler.getMethod("runDelayed", Plugin.class, Consumer.class,
                    Runnable.class, long.class);

            cancelTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
                    .getMethod("cancel");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "vLogin: Folia detectado, mas os schedulers não puderam ser vinculados", ex);
        }
    }

    private static void bindTeleportAsync() {
        try {
            teleportAsync = Player.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException ignored) {
            teleportAsync = null;
        }
    }

    public static void cancel(Object task) {
        if (task == null) {
            return;
        }
        if (task instanceof org.bukkit.scheduler.BukkitTask) {
            ((org.bukkit.scheduler.BukkitTask) task).cancel();
            return;
        }
        if (cancelTask != null) {
            try {
                cancelTask.invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    public static Object runAsync(Plugin plugin, Runnable task) {
        if (!FOLIA || asyncRunNow == null) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
        try {
            return asyncRunNow.invoke(getAsyncScheduler.invoke(null), plugin, consumer(task));
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa assíncrona", ex);
            return null;
        }
    }

    public static Object runAsyncRepeating(Plugin plugin, Runnable task, long delayMillis, long periodMillis) {
        if (!FOLIA || asyncRunAtFixedRate == null) {
            long delayTicks = Math.max(1, delayMillis / 50L);
            long periodTicks = Math.max(1, periodMillis / 50L);
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
        try {
            Object scheduler = getAsyncScheduler.invoke(null);
            return asyncRunAtFixedRate.invoke(scheduler, plugin, consumer(task),
                    Math.max(1L, delayMillis), Math.max(1L, periodMillis), TimeUnit.MILLISECONDS);
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa assíncrona", ex);
            return null;
        }
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (!FOLIA || globalExecute == null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        try {
            globalExecute.invoke(getGlobalRegionScheduler.invoke(null), plugin, task);
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa global", ex);
        }
    }

    public static Object runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA || globalRunDelayed == null) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
        }
        try {
            return globalRunDelayed.invoke(getGlobalRegionScheduler.invoke(null), plugin,
                    consumer(task), Math.max(1L, delayTicks));
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa global adiada", ex);
            return null;
        }
    }

    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (!FOLIA || entityExecute == null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        try {
            Object scheduler = getEntityScheduler.invoke(player);
            entityExecute.invoke(scheduler, plugin, task, null, 1L);
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa do jogador", ex);
        }
    }

    public static void runForPlayerLater(Plugin plugin, Player player, Runnable task, long delayTicks) {
        if (!FOLIA || entityRunDelayed == null) {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
            return;
        }
        try {
            Object scheduler = getEntityScheduler.invoke(player);
            entityRunDelayed.invoke(scheduler, plugin, consumer(task), null, Math.max(1L, delayTicks));
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().log(Level.WARNING, "vLogin: falha ao agendar tarefa adiada do jogador", ex);
        }
    }

    public static void teleport(Player player, Location destination) {
        if (teleportAsync != null) {
            try {
                teleportAsync.invoke(player, destination);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        player.teleport(destination);
    }

    private static Consumer<Object> consumer(Runnable task) {
        return handle -> task.run();
    }
}
