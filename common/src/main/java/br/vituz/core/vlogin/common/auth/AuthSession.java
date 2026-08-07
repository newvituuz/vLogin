package br.vituz.core.vlogin.common.auth;

import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

import java.util.concurrent.atomic.AtomicInteger;

public final class AuthSession {
    private final AuthPlayer player;
    private final String address;
    private final long joinedAt = System.currentTimeMillis();
    private final AtomicInteger failedAttempts = new AtomicInteger();

    private volatile Account account;
    private final java.util.concurrent.atomic.AtomicBoolean authenticated =
            new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean registered;
    private volatile boolean premiumAutologin;
    private volatile boolean premiumVerified;
    private volatile long lastCommandAt;
    private volatile long lastReminderAt;

    public AuthSession(AuthPlayer player, Account account) {
        this.player = player;
        this.address = player.address();
        this.account = account;
        this.registered = account != null && account.isRegistered();
    }

    public AuthPlayer player() {
        return player;
    }

    public String address() {
        return address;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public Account account() {
        return account;
    }

    public void account(Account account) {
        this.account = account;
        this.registered = account != null && account.isRegistered();
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public void authenticated(boolean value) {
        this.authenticated.set(value);
    }

    /**
     * Marca a sessão como autenticada, mas só se ainda não estivesse.
     *
     * Dois caminhos podem chegar aqui ao mesmo tempo (o /logar e uma mensagem do
     * proxy, por exemplo), e sem isso os comandos pós-login rodariam duas vezes.
     *
     * @return true se foi esta chamada que autenticou
     */
    public boolean markAuthenticated() {
        return authenticated.compareAndSet(false, true);
    }

    public boolean isPremiumVerified() {
        return premiumVerified;
    }

    public void premiumVerified(boolean premiumVerified) {
        this.premiumVerified = premiumVerified;
    }

    public boolean isPremiumAutologin() {
        return premiumAutologin;
    }

    public void premiumAutologin(boolean premiumAutologin) {
        this.premiumAutologin = premiumAutologin;
    }

    public int failedAttempts() {
        return failedAttempts.get();
    }

    public int recordFailure() {
        return failedAttempts.incrementAndGet();
    }

    /**
     * Se já passou o intervalo desde o último lembrete. Contar pelo relógio
     * repetia a mensagem quando dois ticks caíam no mesmo segundo.
     */
    public synchronized boolean shouldRemind(long now, long intervalMillis) {
        long since = lastReminderAt == 0 ? now - joinedAt : now - lastReminderAt;
        if (since < intervalMillis) {
            return false;
        }
        lastReminderAt = now;
        return true;
    }

    public synchronized boolean tryCommand(long cooldownMillis) {
        long now = System.currentTimeMillis();
        if (now - lastCommandAt < cooldownMillis) {
            return false;
        }
        lastCommandAt = now;
        return true;
    }

    public int remainingSeconds(int timeoutSeconds) {
        long elapsed = (System.currentTimeMillis() - joinedAt) / 1000L;
        return (int) Math.max(0, timeoutSeconds - elapsed);
    }
}
