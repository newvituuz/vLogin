package br.vituz.core.vlogin.common.premium;

import br.vituz.core.vlogin.common.util.UUIDs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descobre se um nickname pertence a uma conta paga.
 *
 * Falha de consulta é distinguida de "não existe", para que uma queda da
 * Mojang não transforme nicknames originais em livres.
 */
public final class MojangService {
    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(30);
    private static final long OUTAGE_BACKOFF = TimeUnit.MINUTES.toMillis(2);
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final Logger logger;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile long unavailableUntil;

    public MojangService(Logger logger) {
        this.logger = logger;
    }

    public enum Status {
        PREMIUM,
        CRACKED,
        UNKNOWN
    }

    public static final class Result {
        public final Status status;
        public final UUID uniqueId;

        Result(Status status, UUID uniqueId) {
            this.status = status;
            this.uniqueId = uniqueId;
        }

        public boolean isPremium() {
            return status == Status.PREMIUM;
        }
    }

    private static final class CacheEntry {
        final Result result;
        final long expiresAt;

        CacheEntry(Result result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }
    }

    public Result lookup(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.result;
        }
        if (System.currentTimeMillis() < unavailableUntil) {
            return new Result(Status.UNKNOWN, null);
        }

        Result result = request(name);
        if (result.status != Status.UNKNOWN) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                long now = System.currentTimeMillis();
                cache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
                if (cache.size() >= MAX_CACHE_ENTRIES) {
                    cache.clear();
                }
            }
            cache.put(key, new CacheEntry(result, System.currentTimeMillis() + CACHE_TTL));
        }
        return result;
    }

    public Optional<UUID> premiumUniqueId(String name) {
        Result result = lookup(name);
        return result.isPremium() ? Optional.of(result.uniqueId) : Optional.<UUID>empty();
    }

    private Result request(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            return new Result(Status.CRACKED, null);
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(PROFILE_URL + name);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "vLogin");

            int code = connection.getResponseCode();
            if (code == 204 || code == 404) {
                return new Result(Status.CRACKED, null);
            }
            if (code == 429) {
                unavailableUntil = System.currentTimeMillis() + OUTAGE_BACKOFF;
                return new Result(Status.UNKNOWN, null);
            }
            if (code != 200) {
                unavailableUntil = System.currentTimeMillis() + OUTAGE_BACKOFF;
                return new Result(Status.UNKNOWN, null);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            Matcher matcher = ID_PATTERN.matcher(body);
            if (!matcher.find()) {
                return new Result(Status.CRACKED, null);
            }
            UUID uniqueId = UUIDs.parse(matcher.group(1));
            return uniqueId == null
                    ? new Result(Status.UNKNOWN, null)
                    : new Result(Status.PREMIUM, uniqueId);
        } catch (IOException ex) {
            unavailableUntil = System.currentTimeMillis() + OUTAGE_BACKOFF;
            logger.log(Level.FINE, "Mojang lookup failed for " + name, ex);
            return new Result(Status.UNKNOWN, null);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void invalidate(String name) {
        cache.remove(name.toLowerCase(Locale.ROOT));
    }
}
