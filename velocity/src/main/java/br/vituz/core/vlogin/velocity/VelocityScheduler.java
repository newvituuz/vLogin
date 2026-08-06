package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import br.vituz.core.vlogin.common.platform.Scheduler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class VelocityScheduler implements Scheduler {
    private final VLoginVelocity plugin;
    private final ProxyServer server;

    public VelocityScheduler(VLoginVelocity plugin, ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Override
    public void sync(Runnable task) {
        task.run();
    }

    @Override
    public void async(Runnable task) {
        server.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public Cancellable repeatAsync(Runnable task, long delay, long period, TimeUnit unit) {
        ScheduledTask handle = server.getScheduler().buildTask(plugin, task)
                .delay(Duration.ofMillis(unit.toMillis(delay)))
                .repeat(Duration.ofMillis(unit.toMillis(period)))
                .schedule();
        return handle::cancel;
    }

    @Override
    public Cancellable laterSync(Runnable task, long delay, TimeUnit unit) {
        ScheduledTask handle = server.getScheduler().buildTask(plugin, task)
                .delay(Duration.ofMillis(unit.toMillis(delay)))
                .schedule();
        return handle::cancel;
    }
}
