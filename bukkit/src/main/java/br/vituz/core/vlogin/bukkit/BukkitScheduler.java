package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.bukkit.compat.FoliaSupport;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public final class BukkitScheduler implements Scheduler {
    private final VLoginBukkit plugin;

    public BukkitScheduler(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        FoliaSupport.runGlobal(plugin, task);
    }

    @Override
    public void player(AuthPlayer player, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Player handle = Bukkit.getPlayerExact(player.name());
        if (handle == null) {
            return;
        }
        FoliaSupport.runForPlayer(plugin, handle, task);
    }

    @Override
    public void async(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        FoliaSupport.runAsync(plugin, task);
    }

    @Override
    public Cancellable repeatAsync(Runnable task, long delay, long period, TimeUnit unit) {
        Object handle = FoliaSupport.runAsyncRepeating(plugin, task,
                unit.toMillis(delay), unit.toMillis(period));
        return () -> FoliaSupport.cancel(handle);
    }

    @Override
    public Cancellable laterSync(Runnable task, long delay, TimeUnit unit) {
        if (!plugin.isEnabled()) {
            return () -> {
            };
        }
        long delayTicks = Math.max(1, unit.toMillis(delay) / 50L);
        Object handle = FoliaSupport.runGlobalLater(plugin, task, delayTicks);
        return () -> FoliaSupport.cancel(handle);
    }
}
