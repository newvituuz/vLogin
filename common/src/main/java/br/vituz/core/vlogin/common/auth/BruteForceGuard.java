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
    private volatile Settings settings;
    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> blocked = new ConcurrentHashMap<>();

    public BruteForceGuard(Settings settings) {
        this.settings = settings;
    }

    /**
     * Passa a valer a configuração nova depois de um reload.
     *
     * Trocar a referência em vez de recriar o guarda preserva os contadores e os
     * bloqueios: recriar deixaria um /vlogin reload zerar quem estava sob ataque.
     */
    public void settings(Settings settings) {
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
        if (failures.size() >= MAX_TRACKED_ADDRESSES && !failures.containsKey(address)) {
            evictLeastValuable(MAX_TRACKED_ADDRESSES / 10);
        }
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

    /**
     * Teto de endereços acompanhados ao mesmo tempo.
     *
     * Os contadores já somem sozinhos pelo decaimento, mas quem tem muitos endereços
     * à disposição (uma botnet, ou um único bloco IPv6, que costuma dar bilhões deles)
     * consegue criar entradas mais rápido do que elas expiram. O teto transforma isso
     * num consumo limitado em vez de crescer até faltar memória.
     */
    private static final int MAX_TRACKED_ADDRESSES = 50_000;

    /**
     * Descarta os contadores que menos importam, e nunca um bloqueio ativo.
     *
     * A ordem é por número de falhas, e só depois por idade. Despejar simplesmente
     * os mais antigos entregaria o ataque de bandeja: quem errou nove vezes e parou
     * seria o primeiro a sair enquanto o atacante enche o mapa com endereços novos,
     * e o contador da vítima voltaria a zero na décima tentativa.
     *
     * Para empurrar para fora uma entrada perto do limite, o atacante precisaria de
     * milhares de endereços igualmente perto do limite, o que custa uma tentativa de
     * senha para cada uma delas, e cada endereço que chega ao limite vira bloqueio,
     * que esta limpeza não toca.
     */
    private void evictLeastValuable(int howMany) {
        java.util.PriorityQueue<Map.Entry<String, Attempts>> leastValuable =
                new java.util.PriorityQueue<>(howMany + 1, (first, second) -> {
                    int byCount = Integer.compare(count(second.getValue()), count(first.getValue()));
                    return byCount != 0 ? byCount
                            : Long.compare(lastFailure(second.getValue()), lastFailure(first.getValue()));
                });

        for (Map.Entry<String, Attempts> entry : failures.entrySet()) {
            if (blocked.containsKey(entry.getKey())) {
                continue;
            }
            leastValuable.add(entry);
            if (leastValuable.size() > howMany) {
                leastValuable.poll();
            }
        }
        for (Map.Entry<String, Attempts> entry : leastValuable) {
            failures.remove(entry.getKey());
        }
    }

    private static long lastFailure(Attempts attempts) {
        synchronized (attempts) {
            return attempts.lastFailureAt;
        }
    }

    private static int count(Attempts attempts) {
        synchronized (attempts) {
            return attempts.count;
        }
    }

    public int trackedAddresses() {
        return failures.size();
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
