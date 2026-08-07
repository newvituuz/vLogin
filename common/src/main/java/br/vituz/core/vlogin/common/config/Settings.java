package br.vituz.core.vlogin.common.config;

import br.vituz.core.vlogin.common.hash.HashAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;

/**
 * Leitura tipada do config.yml, agrupada pelo que o operador quer mudar
 * e não por qual subsistema lê cada opção.
 */
public final class Settings {
    private final YamlConfig config;
    private final Logger logger;

    public final String language;

    public final StorageSettings storage;
    public final StorageSettings importSource;
    public final String importPreset;
    public final String importTable;
    public final Map<String, String> importColumns;
    public final String table;
    public final Map<String, String> columns;

    /**
     * O layout do nLogin, fixo no código.
     *
     * Está aqui em vez de só no config porque "/vlogin nlogin" precisa funcionar
     * sem o operador ter que descobrir e digitar doze nomes de coluna certos, e
     * errar um deles só apareceria como conta sem senha depois da migração.
     */
    public static final String NLOGIN_TABLE = "nlogin";
    private static final Map<String, String> NLOGIN_COLUMNS;

    static {
        Map<String, String> nlogin = new LinkedHashMap<>();
        nlogin.put("id", "ai");
        nlogin.put("username", "last_name");
        nlogin.put("uuid", "unique_id");
        nlogin.put("premium-uuid", "mojang_id");
        nlogin.put("bedrock-uuid", "bedrock_id");
        nlogin.put("password", "password");
        nlogin.put("address", "last_ip");
        nlogin.put("last-login", "last_seen");
        nlogin.put("registered-at", "creation_date");
        nlogin.put("email", "email");
        nlogin.put("discord", "discord");
        nlogin.put("flags", "settings");
        NLOGIN_COLUMNS = java.util.Collections.unmodifiableMap(nlogin);
    }

    public static Map<String, String> nloginColumns() {
        return NLOGIN_COLUMNS;
    }

    /** Nome da tabela de origem, já resolvido pelo preset. */
    public String sourceTable() {
        if (importPreset.equals("nlogin")) {
            return NLOGIN_TABLE;
        }
        return importPreset.equals("vlogin") ? table : importTable;
    }

    /** Colunas da tabela de origem, já resolvidas pelo preset. */
    public Map<String, String> sourceColumns() {
        if (importPreset.equals("nlogin")) {
            return NLOGIN_COLUMNS;
        }
        return importPreset.equals("vlogin") ? columns : importColumns;
    }

    public final Role role;
    public final String nodeId;
    public final List<String> authServers;
    public final boolean firstServerLock;
    public final boolean allowBackendCommands;
    public final boolean postLoginRedirect;
    public final List<String> postLoginServers;
    public final long postLoginDelayMillis;

    public final boolean redisEnabled;
    public final String redisHost;
    public final int redisPort;
    public final String redisUsername;
    public final String redisPassword;
    public final boolean redisSsl;
    public final int redisDatabase;
    public final String redisChannel;
    public final int redisTimeoutMillis;
    public final int redisMaxConnections;

    public final int loginTimeoutSeconds;
    public final int maxAttempts;
    public final boolean lockoutEnabled;
    public final int lockoutAfterAttempts;
    public final int lockoutMinutes;
    public final boolean sessionEnabled;
    public final int sessionMinutes;
    public final boolean sessionBindAddress;
    public final boolean sameAddressRelogin;

    public final int passwordMinLength;
    public final int passwordMaxLength;
    public final HashAlgorithm algorithm;
    public final int argon2Iterations;
    public final int argon2MemoryMb;
    public final int argon2Parallelism;
    public final int bcryptCost;
    public final int pbkdf2Iterations;
    public final String pbkdf2Mac;
    public final boolean strengthEnabled;
    public final Pattern strengthPattern;
    public final List<String> forbiddenPasswords;

    public final Pattern usernamePattern;
    public final PremiumMode premiumMode;
    public final boolean premiumAutoLogin;
    public final boolean premiumSkipPassword;
    public final boolean premiumAskOnLogin;
    public final boolean premiumStandaloneAuth;
    public final boolean bedrockAutoLogin;
    public final boolean bedrockSkipPassword;
    public final boolean floodgateEnabled;
    public final String floodgatePrefix;
    public final Pattern bedrockUsernamePattern;
    public final boolean bedrockKeepStoredUuid;
    public final boolean bedrockRequireLinked;
    public final boolean addressLimitEnabled;
    public final int addressLimitMax;
    public final boolean addressLimitBlockLogin;
    public final boolean addressLimitExemptRegistered;
    public final boolean addressLimitExemptPremium;
    public final boolean addressLimitExemptBedrock;
    public final List<String> addressAllowlist;

    public final boolean freeze;
    public final boolean hideInventory;
    public final boolean hidePlayers;
    public final boolean hideAttributes;
    public final boolean blindness;
    public final boolean safeSpawn;
    public final boolean useSpawnPoint;
    public final List<String> exemptUsernames;
    public final List<String> allowedInventories;

    public final boolean showTitle;
    public final boolean showActionBar;
    public final boolean showCountdown;
    public final boolean playSounds;
    public final boolean hideChat;
    public final boolean hideJoinMessage;

    public final List<String> consoleOnly;
    public final List<String> onRegister;
    public final List<String> onLogin;
    public final List<String> allowedBeforeLogin;

    public enum PremiumMode {
        OFF, VERIFY, EXCLUSIVE
    }

    public enum Role {
        AUTO, STANDALONE, BACKEND
    }

    public static final class StorageSettings {
        public final String driver;
        public final String file;
        public final String host;
        public final int port;
        public final String database;
        public final String username;
        public final String password;
        public final Map<String, String> options;
        public final int poolMaxConnections;
        public final int poolMinIdle;
        public final long poolMaxLifetimeMillis;
        public final long poolTimeoutMillis;

        StorageSettings(YamlConfig config, String path, String defaultFile) {
            this.driver = config.getString(path + ".driver", "sqlite").trim().toLowerCase(Locale.ROOT);
            this.file = config.getString(path + ".sqlite.file", defaultFile);
            this.host = config.getString(path + ".sql.host", "127.0.0.1");
            this.port = config.getInt(path + ".sql.port", 3306);
            this.database = config.getString(path + ".sql.database", "vlogin");
            this.username = config.getString(path + ".sql.username", "root");
            this.password = config.getString(path + ".sql.password", "");
            this.options = stringMap(config.getSection(path + ".sql.options"));
            this.poolMaxConnections = config.getInt(path + ".sql.pool.max-connections", 10);
            this.poolMinIdle = config.getInt(path + ".sql.pool.min-idle", 5);
            this.poolMaxLifetimeMillis = config.getLong(path + ".sql.pool.max-lifetime-ms", 1800000L);
            this.poolTimeoutMillis = config.getLong(path + ".sql.pool.timeout-ms", 5000L);
        }

        public boolean isRemote() {
            return !driver.equals("sqlite");
        }
    }

    public Settings(YamlConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;

        this.language = config.getString("language", "pt-br");

        this.storage = new StorageSettings(config, "storage", "plugins/vLogin/accounts.db");
        this.importSource = new StorageSettings(config, "storage.import", "plugins/vLogin/import.db");
        this.importPreset = config.getString("storage.import.preset", "nlogin")
                .trim().toLowerCase(Locale.ROOT);
        this.table = config.getString("storage.schema.table", "vituz_accounts");
        this.columns = stringMap(config.getSection("storage.schema.columns"));
        this.importTable = config.getString("storage.import.schema.table", NLOGIN_TABLE);
        this.importColumns = stringMap(config.getSection("storage.import.schema.columns"));

        this.role = enumValue(Role.class, config.getString("network.role", "auto"), Role.AUTO);
        this.nodeId = config.getString("network.node-id", "");
        this.authServers = config.getStringList("network.auth-servers");
        this.firstServerLock = config.getBoolean("network.first-server-lock", true);
        this.allowBackendCommands = config.getBoolean("network.allow-backend-commands", false);
        this.postLoginRedirect = config.getBoolean("network.post-login.enabled", false);
        this.postLoginServers = config.getStringList("network.post-login.servers");
        this.postLoginDelayMillis = config.getLong("network.post-login.delay-ms", 500L);

        this.redisEnabled = config.getBoolean("redis.enabled", false);
        this.redisHost = config.getString("redis.host", "127.0.0.1");
        this.redisPort = config.getInt("redis.port", 6379);
        this.redisUsername = config.getString("redis.username", "");
        this.redisPassword = config.getString("redis.password", "");
        this.redisSsl = config.getBoolean("redis.ssl", false);
        this.redisDatabase = config.getInt("redis.database", 0);
        this.redisChannel = config.getString("redis.channel", "vlogin");
        this.redisTimeoutMillis = config.getInt("redis.timeout-ms", 3000);
        this.redisMaxConnections = config.getInt("redis.pool.max-connections", 8);

        this.loginTimeoutSeconds = config.getInt("login.timeout-seconds", 45);
        this.maxAttempts = Math.max(1, config.getInt("login.max-attempts", 1));
        this.lockoutEnabled = config.getBoolean("login.lockout.enabled", true);
        this.lockoutAfterAttempts = Math.max(1, config.getInt("login.lockout.after-attempts", 10));
        this.lockoutMinutes = config.getInt("login.lockout.minutes", 15);
        this.sessionEnabled = config.getBoolean("login.session.enabled", true);
        this.sessionMinutes = config.getInt("login.session.minutes", 5);
        this.sessionBindAddress = config.getBoolean("login.session.bind-to-address", true);
        this.sameAddressRelogin = config.getBoolean("login.same-address-relogin", true);

        this.passwordMinLength = Math.max(1, config.getInt("password.length.min", 5));
        this.passwordMaxLength = config.getInt("password.length.max", 32);
        this.algorithm = HashAlgorithm.parse(config.getString("password.algorithm", "argon2id"),
                HashAlgorithm.ARGON2ID, logger);
        this.argon2Iterations = Math.max(1, config.getInt("password.argon2.iterations", 3));
        this.argon2MemoryMb = Math.max(1, config.getInt("password.argon2.memory-mb", 64));
        this.argon2Parallelism = Math.max(1, config.getInt("password.argon2.parallelism", 1));
        this.bcryptCost = clamp(config.getInt("password.bcrypt.cost", 10), 4, 31);
        this.pbkdf2Iterations = Math.max(1000, config.getInt("password.pbkdf2.iterations", 10000));
        this.pbkdf2Mac = config.getString("password.pbkdf2.mac", "HmacSHA512");
        this.strengthEnabled = config.getBoolean("password.strength.enabled", false);
        this.strengthPattern = compile(config.getString("password.strength.pattern",
                "(?=\\S*\\d)(?=\\S*[A-Z])(?=\\S*[a-z])(?=\\S*[!@#$%^&*?])\\S*$"), null);
        this.forbiddenPasswords = lowercase(config.getStringList("password.forbidden"));

        if (sessionEnabled && !sessionBindAddress) {
            logger.warning("login.session.bind-to-address está desligado: durante "
                    + sessionMinutes + " minuto(s) após alguém sair, QUALQUER conexão com aquele"
                    + " nickname entra sem senha, venha de onde vier. Se a intenção era proteger"
                    + " quem divide o mesmo IP, o caminho é o contrário: deixe em true e reduza"
                    + " login.session.minutes, ou desligue a sessão.");
        }

        if (algorithm.isBcrypt() && passwordMaxLength > 72) {
            logger.warning("password.length.max é " + passwordMaxLength + ", mas o BCrypt só"
                    + " considera os primeiros 72 caracteres: senhas mais longas passariam a"
                    + " ser equivalentes. Reduza o limite ou use argon2id.");
        }

        this.usernamePattern = compile(config.getString("accounts.username-pattern",
                "([a-zA-Z0-9_]{3,16})"), "([a-zA-Z0-9_]{3,16})");
        this.premiumMode = enumValue(PremiumMode.class,
                config.getString("accounts.premium.mode", "off"), PremiumMode.OFF);
        this.premiumAutoLogin = config.getBoolean("accounts.premium.auto-login", true);
        this.premiumSkipPassword = config.getBoolean("accounts.premium.skip-password", true);
        this.premiumAskOnLogin = config.getBoolean("accounts.premium.ask-on-login", true);
        this.premiumStandaloneAuth = config.getBoolean("accounts.premium.standalone-auth", true);
        this.bedrockAutoLogin = config.getBoolean("accounts.bedrock.auto-login", true);
        this.bedrockSkipPassword = config.getBoolean("accounts.bedrock.skip-password", true);
        this.floodgateEnabled = config.getBoolean("accounts.bedrock.floodgate.enabled", true);
        this.floodgatePrefix = config.getString("accounts.bedrock.floodgate.username-prefix", ".");
        this.bedrockUsernamePattern = compile(
                config.getString("accounts.bedrock.floodgate.username-pattern",
                        "(\\.?[a-zA-Z0-9_ ]{1,20})"), "(\\.?[a-zA-Z0-9_ ]{1,20})");
        this.bedrockKeepStoredUuid =
                config.getBoolean("accounts.bedrock.floodgate.keep-stored-uuid", false);
        this.bedrockRequireLinked =
                config.getBoolean("accounts.bedrock.floodgate.require-linked", false);
        this.addressLimitEnabled = config.getBoolean("accounts.address-limit.enabled", false);
        this.addressLimitMax = config.getInt("accounts.address-limit.max", 3);
        this.addressLimitBlockLogin = config.getBoolean("accounts.address-limit.block-login", false);
        this.addressLimitExemptRegistered = config.getBoolean("accounts.address-limit.exempt.registered", true);
        this.addressLimitExemptPremium = config.getBoolean("accounts.address-limit.exempt.premium", true);
        this.addressLimitExemptBedrock = config.getBoolean("accounts.address-limit.exempt.bedrock", true);
        this.addressAllowlist = config.getStringList("accounts.address-limit.allowlist");

        this.freeze = config.getBoolean("protection.freeze", true);
        this.hideInventory = config.getBoolean("protection.hide-inventory", true);
        this.hidePlayers = config.getBoolean("protection.hide-players", true);
        this.hideAttributes = config.getBoolean("protection.hide-attributes", true);
        this.blindness = config.getBoolean("protection.blindness", false);
        this.safeSpawn = config.getBoolean("protection.safe-spawn", false);
        this.useSpawnPoint = config.getBoolean("protection.spawn-point", true);
        this.exemptUsernames = lowercase(config.getStringList("protection.exempt-usernames"));
        this.allowedInventories = config.getStringList("protection.allowed-inventories");

        this.showTitle = config.getBoolean("interface.title", true);
        this.showActionBar = config.getBoolean("interface.action-bar", true);
        this.showCountdown = config.getBoolean("interface.countdown", true);
        this.playSounds = config.getBoolean("interface.sounds", true);
        this.hideChat = config.getBoolean("interface.hide-chat", true);
        this.hideJoinMessage = config.getBoolean("interface.hide-join-message", true);

        // O piso: subcomandos que podem tomar, apagar ou re-chavear uma conta.
        //
        // Ele é somado ao que estiver no config porque a lista de lá foi escrita numa
        // versão do plugin, e um subcomando perigoso criado depois ficaria de fora dela
        // e, sem esse piso, passaria a ser executável in-game numa simples atualização,
        // sem ninguém pedir por isso. Desligue em console-only-floor para usar apenas a
        // sua lista.
        List<String> floor = Arrays.asList("create", "delete", "setpass",
                "setuuid", "grant", "prune", "import", "nlogin", "export");
        List<String> configured = lowercase(config.getStringList("commands.console-only"));
        if (configured.isEmpty()) {
            this.consoleOnly = lowercase(floor);
        } else if (!config.getBoolean("commands.console-only-floor", true)) {
            this.consoleOnly = configured;
        } else {
            List<String> merged = new ArrayList<>(configured);
            List<String> added = new ArrayList<>();
            for (String sub : floor) {
                if (!merged.contains(sub)) {
                    merged.add(sub);
                    added.add(sub);
                }
            }
            if (!added.isEmpty()) {
                logger.info("Subcomandos mantidos apenas no console por segurança: " + added
                        + ". Use commands.console-only-floor: false para liberá-los.");
            }
            this.consoleOnly = merged;
        }
        this.onRegister = config.getStringList("commands.on-register");
        this.onLogin = config.getStringList("commands.on-login");
        this.allowedBeforeLogin = lowercase(config.getStringList("commands.allowed-before-login"));
    }

    public boolean isBackendBehindProxy() {
        return role == Role.BACKEND;
    }

    public List<String> aliases(String key, String... defaults) {
        List<String> configured = config.getStringList("commands.aliases." + key);
        return configured.isEmpty() ? new ArrayList<>(Arrays.asList(defaults)) : configured;
    }

    public String column(String key, String fallback) {
        String column = columns.get(key);
        return column == null || column.isEmpty() ? fallback : column;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            logger.warning("Unknown value '" + value + "' for " + type.getSimpleName()
                    + ", using " + fallback + ".");
            return fallback;
        }
    }

    private Pattern compile(String regex, String fallback) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            logger.warning("Invalid regex '" + regex + "': " + ex.getDescription());
            return fallback == null ? null : Pattern.compile(fallback);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<String> lowercase(List<String> input) {
        List<String> result = new ArrayList<>(input.size());
        for (String element : input) {
            result.add(element.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static Map<String, String> stringMap(Map<String, Object> input) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
