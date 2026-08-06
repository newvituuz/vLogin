package br.vituz.core.vlogin.common.network;

import br.vituz.core.vlogin.common.config.Settings;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * O barramento de vários nós.
 *
 * Presença e sessões são chaves com TTL, para que um nó que morre não deixe
 * ninguém preso como online. Se o Redis cair, cada operação vira no-op: a
 * rede fica mais burra, não fechada.
 */
public final class RedisBus implements NetworkBus {
    private static final long RECONNECT_DELAY_MILLIS = 5000L;
    private static final long PRESENCE_TTL_SECONDS = 300L;

    private final String nodeId;
    private final Logger logger;
    private final JedisPool pool;
    private final String channel;
    private final String keyPrefix;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Consumer<NetworkMessage> handler;
    private volatile JedisPubSub subscription;
    private Thread subscriber;

    public RedisBus(Settings settings, String nodeId, Logger logger) {
        this.nodeId = nodeId;
        this.logger = logger;
        this.channel = settings.redisChannel + ":events";
        this.keyPrefix = settings.redisChannel + ":";

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(Math.max(2, settings.redisMaxConnections));
        config.setMaxIdle(Math.max(1, settings.redisMaxConnections / 2));
        config.setMinIdle(1);
        config.setTestOnBorrow(true);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(java.time.Duration.ofMillis(settings.redisTimeoutMillis));

        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(settings.redisTimeoutMillis)
                .socketTimeoutMillis(settings.redisTimeoutMillis)
                .user(settings.redisUsername.isEmpty() ? null : settings.redisUsername)
                .password(settings.redisPassword.isEmpty() ? null : settings.redisPassword)
                .database(settings.redisDatabase)
                .ssl(settings.redisSsl)
                .build();

        this.pool = new JedisPool(config, new HostAndPort(settings.redisHost, settings.redisPort),
                clientConfig);
    }

    public void connect() {
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
        startSubscriber();
        logger.info("Redis conectado (" + channel + "), nó " + nodeId + ".");
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    @Override
    public boolean isShared() {
        return true;
    }

    @Override
    public void publish(NetworkMessage.Action action, String player, String extra) {
        NetworkMessage message = new NetworkMessage(nodeId, action, player, extra);
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message.encode());
        } catch (JedisException ex) {
            logger.log(Level.WARNING, "Não foi possível publicar no Redis", ex);
        }
    }

    @Override
    public void subscribe(Consumer<NetworkMessage> handler) {
        this.handler = handler;
    }

    private void startSubscriber() {
        subscriber = new Thread(this::subscribeLoop, "vLogin-redis-subscriber");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    private void subscribeLoop() {
        while (running.get()) {
            try (Jedis jedis = pool.getResource()) {
                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String receivedChannel, String payload) {
                        dispatch(payload);
                    }
                };
                subscription = pubSub;
                jedis.subscribe(pubSub, channel);
            } catch (RuntimeException ex) {
                if (running.get()) {
                    logger.log(Level.WARNING, "Conexão de assinatura do Redis caiu, reconectando", ex);
                }
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void dispatch(String payload) {
        NetworkMessage message = NetworkMessage.decode(payload);
        if (message == null || message.origin.equals(nodeId)) {
            return;
        }
        Consumer<NetworkMessage> current = handler;
        if (current == null) {
            return;
        }
        try {
            current.accept(message);
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "Falha ao processar mensagem do Redis", ex);
        }
    }

    private String presenceKey(String player) {
        return keyPrefix + "online:" + player.toLowerCase(Locale.ROOT);
    }

    private String sessionKey(String player) {
        return keyPrefix + "session:" + player.toLowerCase(Locale.ROOT);
    }

    @Override
    public void markOnline(String player, String address) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(presenceKey(player), PRESENCE_TTL_SECONDS, nodeId + "|" + address);
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível registrar presença", ex);
        }
    }

    @Override
    public void clearOnline(String player) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(presenceKey(player));
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível limpar presença", ex);
        }
    }

    @Override
    public Optional<Presence> presence(String player) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(presenceKey(player));
            if (value == null) {
                return Optional.empty();
            }
            int separator = value.indexOf('|');
            if (separator == -1) {
                return Optional.empty();
            }
            return Optional.of(new Presence(value.substring(0, separator), value.substring(separator + 1)));
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível ler presença", ex);
            return Optional.empty();
        }
    }

    public void refreshPresence(Iterable<String> players, Map<String, String> addresses) {
        try (Jedis jedis = pool.getResource()) {
            for (String player : players) {
                String address = addresses.get(player.toLowerCase(Locale.ROOT));
                jedis.setex(presenceKey(player), PRESENCE_TTL_SECONDS,
                        nodeId + "|" + (address == null ? "" : address));
            }
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível renovar presença", ex);
        }
    }

    @Override
    public void openSession(String player, String address, long durationMillis) {
        if (durationMillis <= 0) {
            return;
        }
        long seconds = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(durationMillis));
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(sessionKey(player), seconds, address);
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível abrir sessão", ex);
        }
    }

    @Override
    public Optional<String> session(String player) {
        try (Jedis jedis = pool.getResource()) {
            return Optional.ofNullable(jedis.get(sessionKey(player)));
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível ler sessão", ex);
            return Optional.empty();
        }
    }

    @Override
    public void clearSession(String player) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(sessionKey(player));
        } catch (JedisException ex) {
            logger.log(Level.FINE, "Não foi possível limpar sessão", ex);
        }
    }

    @Override
    public void close() {
        running.set(false);
        JedisPubSub current = subscription;
        if (current != null && current.isSubscribed()) {
            try {
                current.unsubscribe();
            } catch (RuntimeException ignored) {
            }
        }
        if (subscriber != null) {
            subscriber.interrupt();
        }
        pool.close();
    }
}
