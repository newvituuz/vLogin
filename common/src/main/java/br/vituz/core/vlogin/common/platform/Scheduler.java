package br.vituz.core.vlogin.common.platform;

import java.util.concurrent.TimeUnit;

/**
 * O agendamento de tarefas. Em Folia, cada método cai no scheduler certo.
 */
public interface Scheduler {
    void sync(Runnable task);

    default void player(br.vituz.core.vlogin.common.platform.AuthPlayer player, Runnable task) {
        sync(task);
    }

    void async(Runnable task);

    Cancellable repeatAsync(Runnable task, long delay, long period, TimeUnit unit);

    Cancellable laterSync(Runnable task, long delay, TimeUnit unit);

    interface Cancellable {
        void cancel();
    }
}
