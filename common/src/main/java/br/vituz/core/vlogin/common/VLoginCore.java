package br.vituz.core.vlogin.common;

import br.vituz.core.vlogin.common.auth.AuthManager;
import br.vituz.core.vlogin.common.auth.BruteForceGuard;
import br.vituz.core.vlogin.common.bedrock.BedrockService;
import br.vituz.core.vlogin.common.bedrock.BedrockSupport;
import br.vituz.core.vlogin.common.command.ChangePasswordCommand;
import br.vituz.core.vlogin.common.command.LoginCommand;
import br.vituz.core.vlogin.common.command.LogoutCommand;
import br.vituz.core.vlogin.common.command.OfflineCommand;
import br.vituz.core.vlogin.common.command.PremiumCommand;
import br.vituz.core.vlogin.common.command.RegisterCommand;
import br.vituz.core.vlogin.common.command.VLoginCommand;
import br.vituz.core.vlogin.common.config.Messages;
import br.vituz.core.vlogin.common.config.Settings;
import br.vituz.core.vlogin.common.config.YamlConfig;
import br.vituz.core.vlogin.common.hash.PasswordHashing;
import br.vituz.core.vlogin.common.network.LocalBus;
import br.vituz.core.vlogin.common.network.NetworkBus;
import br.vituz.core.vlogin.common.network.RedisBus;
import br.vituz.core.vlogin.common.platform.Platform;
import br.vituz.core.vlogin.common.premium.MojangService;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import br.vituz.core.vlogin.common.storage.Storage;
import br.vituz.core.vlogin.common.storage.StorageException;
import br.vituz.core.vlogin.common.storage.sql.SqlStorage;
import br.vituz.core.vlogin.common.util.ConsoleLogGuard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monta o plugin: lê a configuração, abre o storage e a rede, registra os
 * comandos. Uma instância por plataforma.
 */
public final class VLoginCore {
    public static final String VERSION = "1.1.0";

    private final Platform platform;
    private final Logger logger;

    private Settings settings;
    private Messages messages;
    private Storage storage;
    private PasswordHashing hashing;
    private AuthManager authManager;
    private MojangService mojangService;
    private BruteForceGuard bruteForceGuard;
    private ProxyMessaging proxyMessaging;
    private NetworkBus bus;
    private BedrockService bedrock;

    public VLoginCore(Platform platform) {
        this.platform = platform;
        this.logger = platform.logger();
    }

    public void enable() {
        File dataFolder = platform.dataFolder();
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta " + dataFolder);
        }

        try {
            loadConfiguration();
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível ler a configuração", ex);
        }

        this.hashing = new PasswordHashing(settings);
        this.mojangService = new MojangService(logger);
        this.bruteForceGuard = new BruteForceGuard(settings);
        this.bedrock = BedrockSupport.detect(settings.floodgateEnabled, settings.floodgatePrefix, logger);
        this.proxyMessaging = new ProxyMessaging(loadSecret());
        this.bus = openBus();

        this.storage = SqlStorage.active(settings, logger, dataFolder);
        try {
            storage.initialize();
        } catch (StorageException ex) {
            throw new IllegalStateException("Não foi possível iniciar o storage: " + ex.getMessage(), ex);
        }

        this.authManager = new AuthManager(this);
        bus.subscribe(authManager::applyNetworkMessage);
        authManager.start();

        registerCommands();

        logger.info("vLogin " + VERSION + " ativo em " + platform.type().displayName()
                + " (" + platform.serverVersion() + ")"
                + (bus.isShared() ? ", Redis ligado" : "") + ".");
    }

    public void disable() {
        if (authManager != null) {
            authManager.shutdown();
        }
        if (platform != null) {
            platform.unregisterCommands();
        }
        if (bus != null) {
            bus.close();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public void reload() throws IOException {
        loadConfiguration();
        this.hashing = new PasswordHashing(settings);
        if (bruteForceGuard != null) {
            bruteForceGuard.settings(settings);
        }
        logger.info("Configuração recarregada.");
    }

    private void loadConfiguration() throws IOException {
        File dataFolder = platform.dataFolder();
        saveDefault("config.yml", new File(dataFolder, "config.yml"));
        saveDefault("messages/pt-br.yml", new File(dataFolder, "messages/pt-br.yml"));
        saveDefault("messages/en.yml", new File(dataFolder, "messages/en.yml"));

        YamlConfig config = YamlConfig.load(new File(dataFolder, "config.yml"), resource("config.yml"));
        this.settings = new Settings(config, logger);

        String languageResource = "messages/" + settings.language + ".yml";
        File languageFile = new File(dataFolder, languageResource);
        if (!languageFile.isFile()) {
            if (resource(languageResource) == null) {
                logger.warning("Idioma '" + settings.language + "' não encontrado; usando pt-br.");
                languageResource = "messages/pt-br.yml";
                languageFile = new File(dataFolder, languageResource);
            } else {
                saveDefault(languageResource, languageFile);
            }
        }
        this.messages = new Messages(YamlConfig.load(languageFile, resource("messages/pt-br.yml")));
    }

    private NetworkBus openBus() {
        String nodeId = settings.nodeId.isEmpty()
                ? platform.type().name().toLowerCase(java.util.Locale.ROOT) + "-"
                + UUID.randomUUID().toString().substring(0, 8)
                : settings.nodeId;

        if (!settings.redisEnabled) {
            return new LocalBus(nodeId);
        }
        RedisBus redis = new RedisBus(settings, nodeId, logger);
        try {
            redis.connect();
            return redis;
        } catch (RuntimeException ex) {
            redis.close();
            logger.log(Level.SEVERE, "Redis está ligado na configuração mas não respondeu."
                    + " Seguindo apenas com estado local. Com mais de um proxy, as sessões"
                    + " não serão compartilhadas.", ex);
            return new LocalBus(nodeId);
        }
    }

    private void registerCommands() {
        LoginCommand login = new LoginCommand(this);
        RegisterCommand register = new RegisterCommand(this);
        ChangePasswordCommand changePassword = new ChangePasswordCommand(this);

        OfflineCommand offline = new OfflineCommand(this);

        register(login);
        register(register);
        register(changePassword);
        register(new LogoutCommand(this));
        register(new PremiumCommand(this));
        register(offline);
        register(new VLoginCommand(this));

        List<String> sensitive = new ArrayList<>();
        sensitive.addAll(login.aliases());
        sensitive.addAll(register.aliases());
        sensitive.addAll(changePassword.aliases());
        sensitive.addAll(offline.aliases());
        sensitive.add("vlogin create");
        sensitive.add("vlogin setpass");
        sensitive.add("vl create");
        sensitive.add("vl setpass");
        ConsoleLogGuard.install(sensitive, logger);
    }

    private void register(br.vituz.core.vlogin.common.command.Command command) {
        if (command.aliases().isEmpty()) {
            return;
        }
        platform.registerCommand(command);
    }

    private InputStream resource(String name) {
        return getClass().getClassLoader().getResourceAsStream("vlogin/" + name);
    }

    private void saveDefault(String resourceName, File target) throws IOException {
        if (target.isFile()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Não foi possível criar " + parent);
        }
        try (InputStream in = resource(resourceName)) {
            if (in == null) {
                logger.warning("Recurso embutido ausente: " + resourceName);
                return;
            }
            try (OutputStream out = Files.newOutputStream(target.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    /**
     * Deixa o arquivo legível só para o dono, onde o sistema souber fazer isso.
     *
     * Este é o segredo que assina as decisões entre proxy e backend; num host
     * compartilhado, a permissão padrão o entrega a qualquer outra conta da máquina.
     */
    private void restrictToOwner(File file) {
        try {
            java.nio.file.Path path = file.toPath();
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                java.nio.file.Files.setPosixFilePermissions(path,
                        java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException | RuntimeException ex) {
            logger.log(Level.FINE, "Não deu para restringir a permissão do secret.key: "
                    + ex.getMessage());
        }
    }

    private String loadSecret() {
        File file = new File(platform.dataFolder(), "secret.key");
        try {
            if (file.isFile()) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content;
                }
            }
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String secret = Base64.getEncoder().encodeToString(random);
            Files.write(file.toPath(), secret.getBytes(StandardCharsets.UTF_8));
            restrictToOwner(file);
            logger.info("secret.key gerado. Copie este arquivo para todos os servidores com vLogin.");
            return secret;
        } catch (IOException ex) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            logger.log(Level.SEVERE, "Não foi possível ler ou criar o secret.key. Um segredo"
                    + " temporário foi gerado em memória: a comunicação entre proxy e backend"
                    + " NÃO vai funcionar até o arquivo poder ser gravado. Verifique as"
                    + " permissões da pasta " + platform.dataFolder() + ".", ex);
            return Base64.getEncoder().encodeToString(random);
        }
    }

    public Platform platform() {
        return platform;
    }

    public Logger logger() {
        return logger;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Storage storage() {
        return storage;
    }

    public PasswordHashing hashing() {
        return hashing;
    }

    public AuthManager auth() {
        return authManager;
    }

    public MojangService mojang() {
        return mojangService;
    }

    public BruteForceGuard bruteForce() {
        return bruteForceGuard;
    }

    public ProxyMessaging proxyMessaging() {
        return proxyMessaging;
    }

    public NetworkBus bus() {
        return bus;
    }

    public BedrockService bedrock() {
        return bedrock;
    }
}
