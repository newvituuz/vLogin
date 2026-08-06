package br.vituz.core.vlogin.common.auth;

import br.vituz.core.vlogin.common.config.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Conta tentativas erradas por endereço e bloqueia quem passa do limite.
 *
 * Os contadores decaem por endereço e NUNCA são zerados por um login bem-
 * sucedido: senão bastaria ter uma conta própria para reiniciar o limite
 * entre chutes na conta alheia. Limpar um endereço é ação administrativa.
 */
public final class BruteForceGuard {
    private final Settings settings;
    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> blocked = new ConcurrentHashMap<>();

    public BruteForceGuard(Settings settings) {
        this.settings = settings;
    }

    private static final class Attempts {
        int count;
        long lastFailureAt;
    }

    private long decayMillis() {
        return TimeUnit.MINUTES.toMillis(Math.max(1, settings.lockoutMinutes));
    }

    public boolean isBlocked(String address) {
        Long until = blocked.get(address);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            blocked.remove(address);
            failures.remove(address);
            return false;
        }
        return true;
    }

    public int remainingMinutes(String address) {
        Long until = blocked.get(address);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toMinutes(remaining) + 1);
    }

    public int recordFailure(String address) {
        long now = System.currentTimeMillis();
        Attempts attempts = failures.computeIfAbsent(address, key -> new Attempts());

        int count;
        synchronized (attempts) {
            if (attempts.lastFailureAt > 0 && now - attempts.lastFailureAt > decayMillis()) {
                attempts.count = 0;
            }
            attempts.lastFailureAt = now;
            count = ++attempts.count;
        }

        if (settings.lockoutEnabled && count >= settings.lockoutAfterAttempts) {
            blocked.put(address, now + TimeUnit.MINUTES.toMillis(settings.lockoutMinutes));
        }
        return count;
    }

    public void reset(String address) {
        failures.remove(address);
        blocked.remove(address);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        blocked.entrySet().removeIf(entry -> entry.getValue() <= now);

        long decay = decayMillis();
        failures.entrySet().removeIf(entry -> {
            if (blocked.containsKey(entry.getKey())) {
                return false;
            }
            Attempts attempts = entry.getValue();
            synchronized (attempts) {
                return now - attempts.lastFailureAt > decay;
            }
        });
    }

    public int failureCount(String address) {
        Attempts attempts = failures.get(address);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            return attempts.count;
        }
    }
}
