package br.vituz.core.vlogin.bungee;

import br.vituz.core.vlogin.common.platform.Scheduler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;

public final class BungeeScheduler implements Scheduler {
    private final VLoginBungee plugin;

    public BungeeScheduler(VLoginBungee plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sync(Runnable task) {
        task.run();
    }

    @Override
    public void async(Runnable task) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, task);
    }

    @Override
    public Cancellable repeatAsync(Runnable task, long delay, long period, TimeUnit unit) {
        ScheduledTask handle = ProxyServer.getInstance().getScheduler()
                .schedule(plugin, task, delay, period, unit);
        return handle::cancel;
    }

    @Override
    public Cancellable laterSync(Runnable task, long delay, TimeUnit unit) {
        ScheduledTask handle = ProxyServer.getInstance().getScheduler()
                .schedule(plugin, task, delay, unit);
        return handle::cancel;
    }
}
