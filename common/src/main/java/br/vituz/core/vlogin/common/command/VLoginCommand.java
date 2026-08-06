package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.auth.AuthManager;
import br.vituz.core.vlogin.common.auth.AuthSession;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.model.AccountSettings;
import br.vituz.core.vlogin.common.config.Settings;
import br.vituz.core.vlogin.common.network.NetworkMessage;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Sender;
import br.vituz.core.vlogin.common.premium.MojangService;
import br.vituz.core.vlogin.common.storage.Storage;
import br.vituz.core.vlogin.common.storage.StorageException;
import br.vituz.core.vlogin.common.storage.sql.SqlStorage;
import br.vituz.core.vlogin.common.util.TimeFormat;
import br.vituz.core.vlogin.common.util.Text;
import br.vituz.core.vlogin.common.util.UUIDs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * O comando administrativo: /vlogin <subcomando>.
 *
 * Quais subcomandos um jogador pode rodar vem de commands.console-only.
 * Tudo que toma, remove ou re-chaveia uma conta pertence a essa lista: uma
 * conta de admin invadida não deve entregar o login de outra pessoa.
 */
public final class VLoginCommand extends Command {
    private final VLoginCore core;
    private final AtomicBoolean busy = new AtomicBoolean();

    private static final Map<String, String> SUBCOMMANDS = new LinkedHashMap<>();

    static {
        SUBCOMMANDS.put("info", "<jogador> - ficha completa da conta");
        SUBCOMMANDS.put("create", "<jogador> <senha> - cria a conta");
        SUBCOMMANDS.put("delete", "<jogador> - apaga a conta");
        SUBCOMMANDS.put("setpass", "<jogador> <senha> - define a senha");
        SUBCOMMANDS.put("setuuid", "<jogador> <uuid|offline|premium> - define o UUID");
        SUBCOMMANDS.put("grant", "<jogador> - libera a entrada sem senha");
        SUBCOMMANDS.put("resetip", "<jogador> - esquece o IP guardado");
        SUBCOMMANDS.put("unblock", "<ip> - tira o bloqueio por tentativas");
        SUBCOMMANDS.put("prune", "<dias> [confirmar] - apaga contas paradas");
        SUBCOMMANDS.put("export", "grava todas as contas em um arquivo");
        SUBCOMMANDS.put("import", "confirmar - traz contas de outro banco");
        SUBCOMMANDS.put("nlogin", "confirmar - traz as contas do nLogin");
        SUBCOMMANDS.put("spawn", "set - marca onde o login acontece");
        SUBCOMMANDS.put("diagnose", "reúne os dados para suporte");
        SUBCOMMANDS.put("reload", "relê a configuração");
        SUBCOMMANDS.put("help", "mostra esta lista");
        SUBCOMMANDS.put("version", "mostra a versão");
    }

    public VLoginCommand(VLoginCore core) {
        super("vlogin", Arrays.asList("vlogin", "vl"), "Administração do vLogin", "vlogin.admin");
        this.core = core;
    }

    @Override
    public void execute(Sender sender, String[] args) {
        if (!sender.hasPermission("vlogin.admin")) {
            sender.sendMessage(core.messages().get(MessageKey.NO_PERMISSION));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_USAGE));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sender.isPlayer() && core.settings().consoleOnly.contains(sub)) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_CONSOLE_ONLY, "command", sub));
            return;
        }
        if (sender.isPlayer() && targetsAnotherAdmin(sub, args)) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_TARGET_IS_ADMIN, "command", sub));
            return;
        }

        long started = System.nanoTime();
        switch (sub) {
            case "help":
                help(sender);
                return;
            case "reload":
                reload(sender);
                return;
            case "version":
                sender.sendMessage(Text.colorize("&7vLogin &b" + VLoginCore.VERSION + " &7em &b"
                        + core.platform().type().displayName()));
                return;
            case "info":
                if (requireArgs(sender, args, 2)) {
                    describeAccount(sender, args[1], started);
                }
                return;
            case "diagnose":
                diagnose(sender, started);
                return;
            case "create":
                if (requireArgs(sender, args, 3)) {
                    create(sender, args[1], args[2]);
                }
                return;
            case "delete":
                if (requireArgs(sender, args, 2)) {
                    delete(sender, args[1]);
                }
                return;
            case "setpass":
                if (requireArgs(sender, args, 3)) {
                    setPassword(sender, args[1], args[2]);
                }
                return;
            case "setuuid":
                if (requireArgs(sender, args, 3)) {
                    setUuid(sender, args[1], args[2]);
                }
                return;
            case "resetip":
                if (requireArgs(sender, args, 2)) {
                    resetAddress(sender, args[1]);
                }
                return;
            case "unblock":
                if (requireArgs(sender, args, 2)) {
                    unblock(sender, args[1]);
                }
                return;
            case "grant":
                if (requireArgs(sender, args, 2)) {
                    grant(sender, args[1]);
                }
                return;
            case "spawn":
                spawn(sender, args);
                return;
            case "prune":
                prune(sender, args);
                return;
            case "export":
                export(sender);
                return;
            case "import":
                importAccounts(sender, args);
                return;
            case "nlogin":
                importFromNLogin(sender, args);
                return;
            default:
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_USAGE));
        }
    }

    private static final List<String> ACCOUNT_TAKEOVER_SUBCOMMANDS =
            Arrays.asList("setpass", "setuuid", "delete", "grant", "resetip");

    /**
     * Barra in-game o que reescreve a conta de outro administrador, mesmo que a
     * lista console-only tenha sido afrouxada. Só alcança alvo online: para quem
     * está fora não há como perguntar a permissão de forma confiável.
     */
    private boolean targetsAnotherAdmin(String sub, String[] args) {
        if (args.length < 2 || !ACCOUNT_TAKEOVER_SUBCOMMANDS.contains(sub)) {
            return false;
        }
        Optional<? extends AuthPlayer> target = core.platform().player(args[1]);
        return target.isPresent() && target.get().hasPermission("vlogin.admin");
    }

    private boolean requireArgs(Sender sender, String[] args, int required) {
        if (args.length >= required) {
            return true;
        }
        sender.sendMessage(core.messages().get(MessageKey.ADMIN_USAGE));
        sender.sendMessage(core.messages().plain(MessageKey.ADMIN_HELP_ENTRY,
                "command", args[0], "description", SUBCOMMANDS.getOrDefault(args[0].toLowerCase(Locale.ROOT), "")));
        return false;
    }

    private void help(Sender sender) {
        sender.sendMessage(core.messages().plain(MessageKey.ADMIN_HELP_HEADER));
        for (Map.Entry<String, String> entry : SUBCOMMANDS.entrySet()) {
            sender.sendMessage(core.messages().plain(MessageKey.ADMIN_HELP_ENTRY,
                    "command", entry.getKey(), "description", entry.getValue()));
        }
    }

    private void reload(Sender sender) {
        try {
            core.reload();
            core.bus().publish(NetworkMessage.Action.RELOAD, "", "");
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_RELOADED));
        } catch (IOException ex) {
            core.logger().log(Level.SEVERE, "Falha ao recarregar a configuração", ex);
            sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
        }
    }

    private void describeAccount(Sender sender, String name, long started) {
        core.platform().scheduler().async(() -> {
            try {
                Optional<Account> found = core.storage().findByName(name);
                if (!found.isPresent()) {
                    sender.sendMessage(core.messages().get(MessageKey.ADMIN_NOT_FOUND, "player", name));
                    return;
                }
                Account account = found.get();
                Optional<AuthSession> session = core.auth().session(account.lastName());
                boolean authenticated = session.isPresent() && session.get().isAuthenticated();

                sender.sendMessage(core.messages().plain(MessageKey.INFO_HEADER,
                        "player", account.lastName()));

                row(sender, MessageKey.INFO_LABEL_STATE, core.messages().plain(authenticated
                        ? MessageKey.INFO_STATE_ONLINE : MessageKey.INFO_STATE_PENDING)
                        + " &8/ &f" + core.messages().plain(typeOf(account)));

                row(sender, MessageKey.INFO_LABEL_LAST_SEEN, account.lastSeen() > 0
                        ? TimeFormat.absolute(account.lastSeen(), "-") + " &7("
                        + TimeFormat.duration(System.currentTimeMillis() - account.lastSeen())
                        + " atrás)"
                        : core.messages().plain(MessageKey.INFO_NEVER));

                row(sender, MessageKey.INFO_LABEL_CREATED, account.creationDate() > 0
                        ? TimeFormat.absolute(account.creationDate(), "-") + " &7("
                        + TimeFormat.duration(System.currentTimeMillis() - account.creationDate())
                        + " atrás)"
                        : core.messages().plain(MessageKey.INFO_NEVER));

                UUID uuid = account.mojangId() != null ? account.mojangId()
                        : account.bedrockId() != null ? account.bedrockId() : account.uniqueId();
                String source = account.mojangId() != null ? "mojang"
                        : account.bedrockId() != null ? "bedrock" : "offline";
                row(sender, MessageKey.INFO_LABEL_UUID,
                        (uuid == null ? "-" : UUIDs.trim(uuid)) + " &8" + source);

                row(sender, MessageKey.INFO_LABEL_ADDRESS,
                        account.lastIp() == null ? "-" : account.lastIp());

                String xuid = account.settings().getString(AccountSettings.BEDROCK_XUID, null);
                if (xuid != null) {
                    row(sender, MessageKey.INFO_LABEL_XBOX, account.settings()
                            .getString(AccountSettings.BEDROCK_GAMERTAG, account.lastName())
                            + " &8xuid " + xuid);
                }
                core.bedrock().identify(account.bedrockId()).ifPresent(identity -> {
                    if (identity.isLinked() && identity.linkedJavaName() != null) {
                        row(sender, MessageKey.INFO_LABEL_PAIRED, identity.linkedJavaName());
                    }
                });

                String neighbours = relatedAccounts(account);
                if (neighbours != null) {
                    row(sender, MessageKey.INFO_LABEL_NEIGHBOURS, neighbours);
                }

                if (session.isPresent()) {
                    core.platform().serverOf(session.get().player()).ifPresent(server ->
                            row(sender, MessageKey.INFO_LABEL_SERVER, server));
                }

                sender.sendMessage(core.messages().plain(MessageKey.INFO_FOOTER,
                        "ms", TimeFormat.millis(System.nanoTime() - started)));
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Falha ao consultar " + name, ex);
                sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            }
        });
    }

    /** Uma linha rotulada da ficha. */
    private void row(Sender sender, MessageKey label, String value) {
        sender.sendMessage(core.messages().plain(MessageKey.INFO_ROW,
                "label", core.messages().plain(label), "value", value));
    }

    private MessageKey typeOf(Account account) {
        if (!account.isRegistered() && !account.isPremium() && !account.isBedrock()) {
            return MessageKey.INFO_TYPE_UNREGISTERED;
        }
        if (account.isPremium()) {
            return MessageKey.INFO_TYPE_PREMIUM;
        }
        if (account.isBedrock()) {
            return MessageKey.INFO_TYPE_BEDROCK;
        }
        return MessageKey.INFO_TYPE_OFFLINE;
    }

    /**
     * As outras contas vistas do mesmo endereço.
     *
     * @return null quando não há nenhuma além da própria, para a linha ser omitida
     */
    private String relatedAccounts(Account account) throws StorageException {
        if (account.lastIp() == null || account.lastIp().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Account other : core.storage().findByAddress(account.lastIp())) {
            if (other.lastName().equalsIgnoreCase(account.lastName())) {
                continue;
            }
            if (text.length() > 0) {
                text.append("&8, &f");
            }
            text.append(other.lastName());
        }
        return text.length() == 0 ? null : text.toString();
    }

    private void diagnose(Sender sender, long started) {
        core.platform().scheduler().async(() -> {
            sender.sendMessage(core.messages().plain(MessageKey.DIAGNOSE_HEADER));
            entry(sender, "versão", VLoginCore.VERSION);
            entry(sender, "plataforma", core.platform().type().displayName()
                    + " &8" + core.platform().serverVersion());
            entry(sender, "java", System.getProperty("java.version"));
            entry(sender, "papel", core.settings().role.name().toLowerCase(Locale.ROOT));
            entry(sender, "banco", core.settings().storage.driver
                    + " &8tabela " + core.settings().table);
            entry(sender, "hashing", core.settings().algorithm.name().toLowerCase(Locale.ROOT));
            entry(sender, "bedrock", core.bedrock().describe());
            entry(sender, "redis", core.settings().redisEnabled
                    ? (core.bus().isShared() ? "conectado" : "ligado, sem resposta")
                    : "desligado");
            entry(sender, "nó", core.bus().nodeId());
            entry(sender, "em jogo", String.valueOf(core.auth().sessions().size()));
            try {
                entry(sender, "contas", String.valueOf(core.storage().count()));
            } catch (StorageException ex) {
                entry(sender, "contas", "erro: " + ex.getMessage());
            }
            sender.sendMessage(core.messages().plain(MessageKey.INFO_FOOTER,
                    "ms", TimeFormat.millis(System.nanoTime() - started)));
        });
    }

    private void entry(Sender sender, String name, String value) {
        sender.sendMessage(core.messages().plain(MessageKey.DIAGNOSE_ROW, "name", name, "value", value));
    }

    private void create(Sender sender, String name, String password) {
        String rejection = core.auth().validatePassword(name, password);
        if (rejection != null) {
            sender.sendMessage(rejection);
            return;
        }
        core.platform().scheduler().async(() -> {
            try {
                Optional<Account> existing = core.storage().findByName(name);
                Account account = existing.orElseGet(() ->
                        Account.create(name, UUIDs.offline(name), "127.0.0.1"));
                account.password(core.hashing().hash(password));
                if (existing.isPresent()) {
                    core.storage().update(account);
                } else {
                    core.storage().insert(account);
                }
                refreshSession(name, account);
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_REGISTERED, "player", name));
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Falha ao registrar " + name, ex);
                sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            }
        });
    }

    private void delete(Sender sender, String name) {
        core.platform().scheduler().async(() -> {
            try {
                if (!core.storage().findByName(name).isPresent()) {
                    sender.sendMessage(core.messages().get(MessageKey.ADMIN_NOT_FOUND, "player", name));
                    return;
                }
                core.storage().delete(name);
                core.bus().clearSession(name);
                core.bus().publish(NetworkMessage.Action.KICK, name,
                        core.messages().plain(MessageKey.KICK_UNREGISTERED));
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_UNREGISTERED, "player", name));
                kickIfOnline(name, MessageKey.KICK_UNREGISTERED);
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Falha ao remover " + name, ex);
                sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            }
        });
    }

    private void setPassword(Sender sender, String name, String password) {
        String rejection = core.auth().validatePassword(name, password);
        if (rejection != null) {
            sender.sendMessage(rejection);
            return;
        }
        withAccount(sender, name, account -> {
            account.password(core.hashing().hash(password));
            core.storage().update(account);
            refreshSession(name, account);
            core.bus().publish(NetworkMessage.Action.ACCOUNT_CHANGED, name, "");
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_PASSWORD_CHANGED, "player", name));
        });
    }

    private void setUuid(Sender sender, String name, String target) {
        withAccount(sender, name, account -> {
            UUID uuid;
            if (target.equalsIgnoreCase("offline")) {
                uuid = UUIDs.offline(account.lastName());
                account.mojangId(null);
            } else if (target.equalsIgnoreCase("premium")) {
                MojangService.Result result = core.mojang().lookup(account.lastName());
                if (!result.isPremium()) {
                    sender.sendMessage(core.messages().get(MessageKey.PREMIUM_NOT_FOUND));
                    return;
                }
                uuid = result.uniqueId;
                account.mojangId(uuid);
            } else {
                uuid = UUIDs.parse(target);
                if (uuid == null) {
                    sender.sendMessage(core.messages().get(MessageKey.ADMIN_UUID_INVALID));
                    return;
                }
            }
            account.uniqueId(uuid);
            core.storage().update(account);
            refreshSession(name, account);
            core.bus().publish(NetworkMessage.Action.ACCOUNT_CHANGED, name, "");
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_UUID_CHANGED,
                    "player", name, "uuid", UUIDs.trim(uuid)));
        });
    }

    private void resetAddress(Sender sender, String name) {
        withAccount(sender, name, account -> {
            core.bruteForce().reset(account.lastIp() == null ? "" : account.lastIp());
            account.lastIp("127.0.0.1");
            core.storage().update(account);
            refreshSession(name, account);
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_ADDRESS_CLEARED, "player", name));
        });
    }

    private void unblock(Sender sender, String address) {
        if (!core.bruteForce().isBlocked(address)) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_NOT_BLOCKED, "address", address));
            return;
        }
        core.bruteForce().reset(address);
        sender.sendMessage(core.messages().get(MessageKey.ADMIN_UNBANNED, "address", address));
    }

    private void grant(Sender sender, String name) {
        Optional<AuthSession> session = core.auth().session(name);
        if (!session.isPresent()) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_NOT_ONLINE, "player", name));
            return;
        }
        core.auth().authenticate(session.get(), AuthManager.AuthReason.ADMIN);
        sender.sendMessage(core.messages().get(MessageKey.ADMIN_FORCED_LOGIN, "player", name));
    }

    private void spawn(Sender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage(core.platform().hasLoginSpawn()
                    ? core.messages().get(MessageKey.ADMIN_SPAWN_SET)
                    : core.messages().get(MessageKey.ADMIN_SPAWN_MISSING));
            return;
        }
        if (!(sender instanceof AuthPlayer)) {
            sender.sendMessage(core.messages().get(MessageKey.PLAYER_ONLY));
            return;
        }
        if (!core.platform().saveLoginSpawn((AuthPlayer) sender)) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_SPAWN_UNSUPPORTED));
            return;
        }
        sender.sendMessage(core.messages().get(MessageKey.ADMIN_SPAWN_SET));
    }

    private void prune(Sender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_PRUNE_USAGE));
            return;
        }
        final int days;
        try {
            days = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_PRUNE_USAGE));
            return;
        }
        if (days < 1) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_PRUNE_USAGE));
            return;
        }
        boolean confirmed = args.length > 2 && args[2].equalsIgnoreCase("confirmar");

        runExclusive(sender, () -> {
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            try {
                if (!confirmed) {
                    int count = core.storage().countInactiveBefore(cutoff);
                    sender.sendMessage(core.messages().get(MessageKey.ADMIN_PRUNE_PREVIEW,
                            "count", count, "days", days));
                    return;
                }
                int removed = core.storage().deleteInactiveBefore(cutoff);
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_PRUNED, "count", removed));
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Falha ao remover contas inativas", ex);
                sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            }
        });
    }

    private void export(Sender sender) {
        runExclusive(sender, () -> {
            File folder = new File(core.platform().dataFolder(), "backups");
            if (!folder.isDirectory() && !folder.mkdirs()) {
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_EXPORT_FAILED,
                        "error", "não foi possível criar " + folder));
                return;
            }
            String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(new Date());
            File target = new File(folder, "accounts-" + stamp + ".csv");

            try {
                List<Account> accounts = core.storage().findAll();
                try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                    writer.write("username,uuid,premium_uuid,bedrock_uuid,password,address,"
                            + "last_login,registered_at,email,discord,flags");
                    writer.newLine();
                    for (Account account : accounts) {
                        writer.write(csv(account));
                        writer.newLine();
                    }
                }
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_EXPORT_DONE,
                        "count", accounts.size(), "file", target.getName()));
            } catch (StorageException | IOException ex) {
                core.logger().log(Level.SEVERE, "Falha ao gerar o backup", ex);
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_EXPORT_FAILED,
                        "error", String.valueOf(ex.getMessage())));
            }
        });
    }

    private String csv(Account account) {
        return String.join(",",
                quote(account.lastName()),
                quote(UUIDs.trim(account.uniqueId())),
                quote(UUIDs.trim(account.mojangId())),
                quote(UUIDs.trim(account.bedrockId())),
                quote(account.password()),
                quote(account.lastIp()),
                String.valueOf(account.lastSeen()),
                String.valueOf(account.creationDate()),
                quote(account.email()),
                quote(account.discord()),
                quote(account.settings().serialize()));
    }

    private String quote(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (!escaped.isEmpty() && "=+-@\t\r".indexOf(escaped.charAt(0)) > -1) {
            escaped = "'" + escaped;
        }
        return "\"" + escaped + "\"";
    }

    private void importAccounts(Sender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_USAGE));
            return;
        }
        copyFrom(sender, core.settings().sourceTable(), core.settings().sourceColumns());
    }

    private void importFromNLogin(Sender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_NLOGIN_USAGE));
            return;
        }
        copyFrom(sender, Settings.NLOGIN_TABLE, Settings.nloginColumns());
    }

    /**
     * Copia as contas de um banco de origem para o storage ativo.
     *
     * A origem é aberta somente para leitura, e cada conta é copiada por conta
     * própria: uma linha problemática é registrada e a migração segue, em vez de
     * abortar no meio e deixar metade das contas para trás sem ninguém saber
     * quais. O nickname já existente aqui nunca é sobrescrito: quem já jogou no
     * vLogin tem a senha que criou aqui, não a antiga.
     */
    private static final int CHUNK = 2000;

    private void copyFrom(Sender sender, String table, java.util.Map<String, String> columns) {
        runExclusive(sender, () -> {
            Settings settings = core.settings();
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_START,
                    "table", table, "driver", settings.importSource.driver));

            Storage source;
            try {
                // O construtor recusa nome de tabela ou de coluna inválido, e isso vem
                // do config: sem este catch o erro morreria na thread e o operador
                // ficaria olhando para um comando que não respondeu nada.
                source = new SqlStorage(settings.importSource, table, columns,
                        core.logger(), core.platform().dataFolder(), true);
            } catch (RuntimeException ex) {
                core.logger().log(Level.SEVERE, "Origem de importação inválida", ex);
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_FAILED,
                        "error", String.valueOf(ex.getMessage())));
                return;
            }

            long started = System.currentTimeMillis();
            int imported = 0;
            int skipped = 0;
            int failed = 0;
            try {
                source.initialize();
                List<Account> accounts = source.findAll();
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_FOUND,
                        "count", accounts.size()));

                java.util.Set<String> here = core.storage().names();
                List<Account> pending = new ArrayList<>();
                for (Account account : accounts) {
                    String name = account.lastName();
                    if (name == null || name.isEmpty() || !here.add(name.toLowerCase(Locale.ROOT))) {
                        skipped++;
                        continue;
                    }
                    account.id(-1);
                    // Uma sessão aberta na origem não atravessa a migração: quem chega
                    // aqui digita a senha uma vez. Copiar o flag deixaria a conta
                    // entrável sem senha pelo resto do prazo da sessão.
                    account.settings().setLong(AccountSettings.SESSION_STARTED, 0L);
                    account.settings().setString(AccountSettings.SESSION_ADDRESS, null);
                    pending.add(account);
                }

                int done = 0;
                while (done < pending.size()) {
                    List<Account> chunk = pending.subList(done,
                            Math.min(done + CHUNK, pending.size()));
                    imported += core.storage().insertAll(chunk);
                    done += chunk.size();
                    if (done < pending.size()) {
                        sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_PROGRESS,
                                "done", done, "total", pending.size()));
                    }
                }
                failed = pending.size() - imported;
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_DONE,
                        "imported", imported, "skipped", skipped, "failed", failed,
                        "seconds", (System.currentTimeMillis() - started) / 1000));
            } catch (StorageException | RuntimeException ex) {
                core.logger().log(Level.SEVERE, "Falha na migração", ex);
                sender.sendMessage(core.messages().get(MessageKey.ADMIN_IMPORT_FAILED,
                        "error", String.valueOf(ex.getMessage())));
            } finally {
                source.close();
            }
        });
    }

    private interface AccountAction {
        void apply(Account account) throws StorageException;
    }

    private void withAccount(Sender sender, String name, AccountAction action) {
        core.platform().scheduler().async(() -> {
            try {
                Optional<Account> found = core.storage().findByName(name);
                if (!found.isPresent()) {
                    sender.sendMessage(core.messages().get(MessageKey.ADMIN_NOT_FOUND, "player", name));
                    return;
                }
                action.apply(found.get());
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Falha na operação sobre " + name, ex);
                sender.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            }
        });
    }

    private void runExclusive(Sender sender, Runnable task) {
        if (!busy.compareAndSet(false, true)) {
            sender.sendMessage(core.messages().get(MessageKey.ADMIN_BUSY));
            return;
        }
        core.platform().scheduler().async(() -> {
            try {
                task.run();
            } finally {
                busy.set(false);
            }
        });
    }

    private void kickIfOnline(String name, MessageKey reason) {
        Optional<? extends AuthPlayer> online = core.platform().player(name);
        if (online.isPresent()) {
            AuthPlayer player = online.get();
            core.platform().scheduler().player(player,
                    () -> player.kick(core.messages().plain(reason)));
        }
    }

    private void refreshSession(String name, Account account) {
        core.auth().session(name).ifPresent(session -> session.account(account));
    }

    @Override
    public List<String> suggest(Sender sender, String[] args) {
        // Sem isto, qualquer jogador completa "/vlogin " e enxerga os subcomandos e a
        // lista de quem está online. A permissão só era conferida na execução.
        if (!sender.hasPermission("vlogin.admin")) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String candidate : SUBCOMMANDS.keySet()) {
                if (candidate.startsWith(prefix)) {
                    result.add(candidate);
                }
            }
            return result;
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (AuthPlayer player : core.platform().onlinePlayers()) {
                if (player.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.name());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
