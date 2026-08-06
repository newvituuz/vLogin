package br.vituz.core.vlogin.common.storage.sql;

import br.vituz.core.vlogin.common.config.Settings;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.model.AccountSettings;
import br.vituz.core.vlogin.common.storage.Storage;
import br.vituz.core.vlogin.common.storage.StorageException;
import br.vituz.core.vlogin.common.util.UUIDs;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * As contas em SQL: SQLite, MySQL e MariaDB.
 *
 * O nome da tabela e das colunas vem do config, então dá para apontar
 * direto para o banco de outro plugin de login. Colunas que faltarem são
 * criadas na inicialização, em vez de exigir tabela nova.
 */
public final class SqlStorage implements Storage {
    private final Settings.StorageSettings connection;
    private final Logger logger;
    private final SqlDialect dialect;
    private final File dataFolder;
    private final boolean readOnly;
    private Set<String> absentColumns = java.util.Collections.emptySet();

    private HikariDataSource dataSource;

    private final String table;
    private final String colId;
    private final String colUsername;
    private final String colUuid;
    private final String colPremiumUuid;
    private final String colBedrockUuid;
    private final String colPassword;
    private final String colAddress;
    private final String colLastLogin;
    private final String colRegisteredAt;
    private final String colEmail;
    private final String colDiscord;
    private final String colFlags;

    public SqlStorage(Settings.StorageSettings connection, String table, Map<String, String> columns,
                      Logger logger, File dataFolder) {
        this(connection, table, columns, logger, dataFolder, false);
    }

    public SqlStorage(Settings.StorageSettings connection, String table, Map<String, String> columns,
                      Logger logger, File dataFolder, boolean readOnly) {
        this.connection = connection;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.readOnly = readOnly;
        this.dialect = SqlDialect.parse(connection.driver);

        this.table = checkIdentifier(table, "tabela");
        this.colId = column(columns, "id", "id");
        this.colUsername = column(columns, "username", "nickname");
        this.colUuid = column(columns, "uuid", "player_id");
        this.colPremiumUuid = column(columns, "premium-uuid", "mojang_id");
        this.colBedrockUuid = column(columns, "bedrock-uuid", "xbox_id");
        this.colPassword = column(columns, "password", "secret");
        this.colAddress = column(columns, "address", "last_address");
        this.colLastLogin = column(columns, "last-login", "last_login");
        this.colRegisteredAt = column(columns, "registered-at", "created_at");
        this.colEmail = column(columns, "email", "email");
        this.colDiscord = column(columns, "discord", "discord");
        this.colFlags = column(columns, "flags", "flags");
    }

    public static SqlStorage active(Settings settings, Logger logger, File dataFolder) {
        return new SqlStorage(settings.storage, settings.table, settings.columns, logger, dataFolder);
    }

    private static final java.util.regex.Pattern IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z0-9_$]{1,64}");

    private static String checkIdentifier(String name, String what) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Nome inválido para " + what + ": '" + name
                    + "'. Use apenas letras, números e underscore.");
        }
        return name;
    }

    private static String column(Map<String, String> columns, String key, String fallback) {
        String name = columns.get(key);
        return checkIdentifier(name == null || name.isEmpty() ? fallback : name, "coluna " + key);
    }

    @Override
    public void initialize() throws StorageException {
        try {
            this.dataSource = buildDataSource();
        } catch (SQLException ex) {
            throw new StorageException("Não foi possível abrir a conexão " + dialect, ex);
        }

        try (Connection handle = dataSource.getConnection()) {
            if (readOnly) {
                requireExistingColumns(handle);
            } else {
                createTable(handle);
                migrateColumns(handle);
            }
        } catch (SQLException ex) {
            throw new StorageException("Não foi possível preparar a tabela de contas", ex);
        }
        logger.info("Storage pronto (" + dialect.name().toLowerCase(Locale.ROOT) + ", tabela " + table
                + (readOnly ? ", somente leitura" : "") + ").");
    }

    private HikariDataSource buildDataSource() throws SQLException {
        Properties properties = new Properties();
        String url;

        if (dialect.isRemote()) {
            String scheme = dialect == SqlDialect.MARIADB ? "mariadb" : "mysql";
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, String> entry : connection.options.entrySet()) {
                query.append(query.length() == 0 ? '?' : '&')
                        .append(entry.getKey()).append('=').append(entry.getValue());
            }
            url = "jdbc:" + scheme + "://" + connection.host + ":" + connection.port
                    + "/" + connection.database + query;
            properties.setProperty("user", connection.username);
            properties.setProperty("password", connection.password);
        } else {
            File file = new File(connection.file);
            File parent = file.getParentFile();
            if (parent == null) {
                file = new File(dataFolder, file.getName());
            } else if (!parent.isDirectory() && !parent.mkdirs()) {
                logger.warning("Não foi possível criar o diretório " + parent);
            }
            url = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        DriverDataSource driverDataSource = new DriverDataSource(dialect.driverClass(), url, properties);

        HikariConfig config = new HikariConfig();
        config.setPoolName("vLogin-" + (readOnly ? "src-" : "") + table);
        config.setDataSource(driverDataSource);
        config.setConnectionTimeout(connection.poolTimeoutMillis);
        if (dialect.isRemote()) {
            int max = Math.max(1, connection.poolMaxConnections);
            config.setMaximumPoolSize(max);
            config.setMinimumIdle(Math.min(Math.max(0, connection.poolMinIdle), max));
            config.setMaxLifetime(connection.poolMaxLifetimeMillis);
        } else {
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
        }
        return new HikariDataSource(config);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void createTable(Connection handle) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`" + colId + "` " + dialect.autoIncrementColumn() + ", "
                + "`" + colUsername + "` VARCHAR(255) NOT NULL, "
                + "`" + colUuid + "` VARCHAR(32), "
                + "`" + colPremiumUuid + "` VARCHAR(32), "
                + "`" + colBedrockUuid + "` VARCHAR(32), "
                + "`" + colPassword + "` VARCHAR(255), "
                + "`" + colAddress + "` VARCHAR(60) DEFAULT '127.0.0.1', "
                + "`" + colLastLogin + "` TIMESTAMP NULL DEFAULT NULL, "
                + "`" + colRegisteredAt + "` TIMESTAMP NULL DEFAULT NULL, "
                + "`" + colEmail + "` VARCHAR(255), "
                + "`" + colDiscord + "` VARCHAR(255), "
                + "`" + colFlags + "` TEXT"
                + ")";
        try (Statement statement = handle.createStatement()) {
            statement.executeUpdate(sql);
        }
        createUniqueUsernameIndex(handle);
        createIndex(handle, table + "_uuid_idx", colUuid);
        createIndex(handle, table + "_premium_idx", colPremiumUuid);
        createIndex(handle, table + "_address_idx", colAddress);
    }

    /**
     * Índice único sobre o nickname.
     *
     * É o que impede, no próprio banco, que dois registros simultâneos criem duas
     * contas com o mesmo nome. A checagem no código fecha a maior parte da janela,
     * mas só uma restrição do banco fecha por completo. Numa tabela herdada que já
     * tenha duplicatas a criação falha; nesse caso o aviso diz o que limpar.
     */
    private void createUniqueUsernameIndex(Connection handle) {
        String sql = dialect == SqlDialect.SQLITE
                ? "CREATE UNIQUE INDEX IF NOT EXISTS `" + table + "_username_idx` ON `" + table
                + "` (`" + colUsername + "` COLLATE NOCASE)"
                : "CREATE UNIQUE INDEX `" + table + "_username_idx` ON `" + table
                + "` (`" + colUsername + "`)";
        try (Statement statement = handle.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            // Já existe (o caso normal depois da primeira execução), ou a tabela tem
            // nicknames repetidos. Só o segundo caso merece atenção.
            logger.log(Level.FINE, "Índice único de nickname não criado: " + ex.getMessage());
            createIndex(handle, table + "_username_lookup_idx", colUsername);
        }
    }

    private void createIndex(Connection handle, String name, String column) {
        String sql = dialect == SqlDialect.SQLITE
                ? "CREATE INDEX IF NOT EXISTS `" + name + "` ON `" + table + "` (`" + column + "`)"
                : "CREATE INDEX `" + name + "` ON `" + table + "` (`" + column + "`)";
        try (Statement statement = handle.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            logger.log(Level.FINE, "Índice " + name + " não criado: " + ex.getMessage());
        }
    }

    private void migrateColumns(Connection handle) throws SQLException {
        Set<String> existing = new HashSet<>();
        DatabaseMetaData metaData = handle.getMetaData();
        try (ResultSet columns = metaData.getColumns(handle.getCatalog(), null, table, null)) {
            while (columns.next()) {
                existing.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (existing.isEmpty()) {
            return;
        }

        addColumnIfMissing(handle, existing, colPremiumUuid, "VARCHAR(32)");
        addColumnIfMissing(handle, existing, colBedrockUuid, "VARCHAR(32)");
        addColumnIfMissing(handle, existing, colEmail, "VARCHAR(255)");
        addColumnIfMissing(handle, existing, colDiscord, "VARCHAR(255)");
        addColumnIfMissing(handle, existing, colFlags, "TEXT");
    }

    private void addColumnIfMissing(Connection handle, Set<String> existing, String column, String type) {
        if (existing.contains(column.toLowerCase(Locale.ROOT))) {
            return;
        }
        try (Statement statement = handle.createStatement()) {
            statement.executeUpdate("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + type);
            logger.info("Coluna '" + column + "' adicionada em " + table + ".");
        } catch (SQLException ex) {
            logger.warning("Não foi possível adicionar a coluna '" + column + "': " + ex.getMessage());
        }
    }

    /**
     * Confere a tabela de origem sem tocar nela.
     *
     * Uma migração não pode criar tabela nem rodar ALTER no banco de quem está
     * saindo: se algo der errado no meio, o plugin antigo tem que continuar
     * funcionando exatamente como estava. Colunas que faltarem viram NULL na
     * leitura, e só nickname e senha são exigidos de fato.
     */
    private void requireExistingColumns(Connection handle) throws SQLException, StorageException {
        Set<String> existing = new HashSet<>();
        DatabaseMetaData metaData = handle.getMetaData();
        try (ResultSet columns = metaData.getColumns(handle.getCatalog(), null, table, null)) {
            while (columns.next()) {
                existing.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (existing.isEmpty()) {
            throw new StorageException("A tabela '" + table + "' não existe no banco de origem.");
        }

        Set<String> missing = new HashSet<>();
        for (String column : new String[]{colId, colUsername, colUuid, colPremiumUuid, colBedrockUuid,
                colPassword, colAddress, colLastLogin, colRegisteredAt, colEmail, colDiscord, colFlags}) {
            if (!existing.contains(column.toLowerCase(Locale.ROOT))) {
                missing.add(column);
            }
        }
        if (missing.contains(colUsername) || missing.contains(colPassword) || missing.contains(colId)) {
            throw new StorageException("A tabela '" + table + "' não tem as colunas essenciais: "
                    + missing + ". Ajuste storage.import.schema.columns no config.");
        }
        if (!missing.isEmpty()) {
            logger.warning("Colunas ausentes na origem, serão lidas como vazias: " + missing);
        }
        this.absentColumns = missing;
    }

    private void refuseWrite() throws StorageException {
        if (readOnly) {
            throw new StorageException("Este storage foi aberto somente para leitura.");
        }
    }

    private String read(String column) {
        return absentColumns.contains(column) ? "NULL AS `" + column + "`" : "`" + column + "`";
    }

    /**
     * Contas paradas desde antes de um instante.
     *
     * Uma conta importada de outro plugin pode não ter data de último login; nesse
     * caso vale a data de registro. Sem data nenhuma, a conta nunca é apagada: não
     * dá para chamar de inativa o que não se sabe datar.
     */
    private String inactiveWhere(String action) {
        return action + " `" + table + "` WHERE COALESCE(`" + colLastLogin + "`, `"
                + colRegisteredAt + "`) IS NOT NULL AND COALESCE(`" + colLastLogin + "`, `"
                + colRegisteredAt + "`) < ?";
    }

    private String selectPrefix() {
        return "SELECT " + read(colId) + ", " + read(colUsername) + ", " + read(colUuid) + ", "
                + read(colPremiumUuid) + ", " + read(colBedrockUuid) + ", " + read(colPassword) + ", "
                + read(colAddress) + ", " + read(colLastLogin) + ", " + read(colRegisteredAt) + ", "
                + read(colEmail) + ", " + read(colDiscord) + ", " + read(colFlags)
                + " FROM `" + table + "`";
    }

    private String usernameMatches() {
        return dialect == SqlDialect.SQLITE
                ? "`" + colUsername + "` = ? COLLATE NOCASE"
                : "`" + colUsername + "` = ?";
    }

    @Override
    public Optional<Account> findByName(String name) throws StorageException {
        // Ordenado para que, se a tabela tiver duplicatas vindas de outro plugin, a
        // conta mais antiga vença sempre, e não uma linha escolhida ao acaso.
        return queryOne(selectPrefix() + " WHERE " + usernameMatches()
                + " ORDER BY `" + colId + "` ASC LIMIT 1", name);
    }

    @Override
    public Optional<Account> findByUniqueId(UUID uniqueId) throws StorageException {
        if (uniqueId == null) {
            return Optional.empty();
        }
        return queryOne(selectPrefix() + " WHERE `" + colUuid + "` = ? LIMIT 1", UUIDs.trim(uniqueId));
    }

    @Override
    public Optional<Account> findByMojangId(UUID mojangId) throws StorageException {
        if (mojangId == null) {
            return Optional.empty();
        }
        return queryOne(selectPrefix() + " WHERE `" + colPremiumUuid + "` = ? LIMIT 1", UUIDs.trim(mojangId));
    }

    private Optional<Account> queryOne(String sql, String parameter) throws StorageException {
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.<Account>empty();
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao consultar a conta", ex);
        }
    }

    @Override
    public List<Account> findByAddress(String address) throws StorageException {
        List<Account> accounts = new ArrayList<>();
        String sql = selectPrefix() + " WHERE `" + colAddress + "` = ? ORDER BY `" + colLastLogin + "` DESC LIMIT 25";
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setString(1, address);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    accounts.add(read(result));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao consultar contas do IP " + address, ex);
        }
        return accounts;
    }

    @Override
    public List<Account> findAll() throws StorageException {
        List<Account> accounts = new ArrayList<>();
        try (Connection handle = dataSource.getConnection();
             Statement statement = handle.createStatement();
             ResultSet result = statement.executeQuery(selectPrefix())) {
            while (result.next()) {
                accounts.add(read(result));
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao ler a tabela de contas", ex);
        }
        return accounts;
    }

    @Override
    public int countByAddress(String address) throws StorageException {
        String sql = "SELECT COUNT(*) FROM `" + table + "` WHERE `" + colAddress + "` = ?";
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setString(1, address);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao contar contas de " + address, ex);
        }
    }

    @Override
    public long count() throws StorageException {
        try (Connection handle = dataSource.getConnection();
             Statement statement = handle.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException ex) {
            throw new StorageException("Falha ao contar contas", ex);
        }
    }

    @Override
    public int countInactiveBefore(long timestamp) throws StorageException {
        String sql = inactiveWhere("SELECT COUNT(*) FROM");
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setTimestamp(1, new Timestamp(timestamp));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao contar contas inativas", ex);
        }
    }

    @Override
    public int deleteInactiveBefore(long timestamp) throws StorageException {
        refuseWrite();
        String sql = inactiveWhere("DELETE FROM");
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setTimestamp(1, new Timestamp(timestamp));
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Falha ao remover contas inativas", ex);
        }
    }

    @Override
    public void insert(Account account) throws StorageException {
        refuseWrite();
        String sql = "INSERT INTO `" + table + "` (`" + colUsername + "`, `" + colUuid + "`, `" + colPremiumUuid
                + "`, `" + colBedrockUuid + "`, `" + colPassword + "`, `" + colAddress + "`, `" + colLastLogin
                + "`, `" + colRegisteredAt + "`, `" + colEmail + "`, `" + colDiscord + "`, `" + colFlags
                + "`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWritableColumns(statement, account);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    account.id(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao criar a conta " + account.lastName(), ex);
        }
    }

    @Override
    public int insertAll(List<Account> accounts) throws StorageException {
        refuseWrite();
        if (accounts.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO `" + table + "` (`" + colUsername + "`, `" + colUuid + "`, `" + colPremiumUuid
                + "`, `" + colBedrockUuid + "`, `" + colPassword + "`, `" + colAddress + "`, `" + colLastLogin
                + "`, `" + colRegisteredAt + "`, `" + colEmail + "`, `" + colDiscord + "`, `" + colFlags
                + "`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int written = 0;
        try (Connection handle = dataSource.getConnection()) {
            boolean autoCommit = handle.getAutoCommit();
            handle.setAutoCommit(false);
            try {
                for (int from = 0; from < accounts.size(); from += BATCH_SIZE) {
                    List<Account> slice =
                            accounts.subList(from, Math.min(from + BATCH_SIZE, accounts.size()));
                    written += writeBatch(handle, sql, slice);
                }
            } finally {
                handle.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao inserir " + accounts.size() + " contas", ex);
        }
        return written;
    }

    /**
     * Grava um lote; se ele falhar, grava as contas dele uma a uma.
     *
     * Um único registro problemático (nickname repetido numa tabela herdada,
     * campo grande demais) invalida o lote inteiro no JDBC. Numa migração isso
     * custaria centenas de contas boas por causa de uma ruim, então o lote é a
     * via rápida e a repetição individual é a rede de segurança.
     */
    private int writeBatch(Connection handle, String sql, List<Account> slice) throws SQLException {
        try (PreparedStatement statement = handle.prepareStatement(sql)) {
            for (Account account : slice) {
                bindWritableColumns(statement, account);
                statement.addBatch();
            }
            int written = applied(statement.executeBatch());
            handle.commit();
            return written;
        } catch (SQLException ex) {
            handle.rollback();
            logger.log(Level.FINE, "Lote recusado, repetindo conta a conta: " + ex.getMessage());
        }

        int written = 0;
        for (Account account : slice) {
            try (PreparedStatement statement = handle.prepareStatement(sql)) {
                bindWritableColumns(statement, account);
                statement.executeUpdate();
                handle.commit();
                written++;
            } catch (SQLException ex) {
                handle.rollback();
                logger.warning("Conta '" + account.lastName() + "' recusada pelo banco: "
                        + ex.getMessage());
            }
        }
        return written;
    }

    private static final int BATCH_SIZE = 500;

    private static int applied(int[] results) {
        int total = 0;
        for (int result : results) {
            // SUCCESS_NO_INFO (-2) é o que o MySQL devolve num batch; conta como uma.
            if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                total++;
            }
        }
        return total;
    }

    @Override
    public Set<String> names() throws StorageException {
        Set<String> found = new HashSet<>();
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(
                     "SELECT `" + colUsername + "` FROM `" + table + "`");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                if (name != null) {
                    found.add(name.toLowerCase(Locale.ROOT));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Falha ao listar os nicknames", ex);
        }
        return found;
    }

    @Override
    public void update(Account account) throws StorageException {
        refuseWrite();
        boolean byId = account.id() > 0;
        String sql = "UPDATE `" + table + "` SET `" + colUsername + "` = ?, `" + colUuid + "` = ?, `"
                + colPremiumUuid + "` = ?, `" + colBedrockUuid + "` = ?, `" + colPassword + "` = ?, `"
                + colAddress + "` = ?, `" + colLastLogin + "` = ?, `" + colRegisteredAt + "` = ?, `"
                + colEmail + "` = ?, `" + colDiscord + "` = ?, `" + colFlags + "` = ? WHERE "
                + (byId ? "`" + colId + "` = ?" : usernameMatches());
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            bindWritableColumns(statement, account);
            if (byId) {
                statement.setLong(12, account.id());
            } else {
                statement.setString(12, account.lastName());
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Falha ao atualizar a conta " + account.lastName(), ex);
        }
    }

    @Override
    public void delete(String name) throws StorageException {
        refuseWrite();
        String sql = "DELETE FROM `" + table + "` WHERE " + usernameMatches();
        try (Connection handle = dataSource.getConnection();
             PreparedStatement statement = handle.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Falha ao remover a conta " + name, ex);
        }
    }

    private void bindWritableColumns(PreparedStatement statement, Account account) throws SQLException {
        statement.setString(1, account.lastName());
        statement.setString(2, UUIDs.trim(account.uniqueId()));
        statement.setString(3, UUIDs.trim(account.mojangId()));
        statement.setString(4, UUIDs.trim(account.bedrockId()));
        statement.setString(5, account.password());
        statement.setString(6, account.lastIp());
        statement.setTimestamp(7, account.lastSeen() > 0 ? new Timestamp(account.lastSeen()) : null);
        statement.setTimestamp(8, account.creationDate() > 0 ? new Timestamp(account.creationDate()) : null);
        statement.setString(9, account.email());
        statement.setString(10, account.discord());
        statement.setString(11, account.settings().serialize());
    }

    private Account read(ResultSet result) throws SQLException {
        Account account = new Account();
        account.id(result.getLong(colId));
        account.lastName(result.getString(colUsername));
        account.uniqueId(UUIDs.parse(result.getString(colUuid)));
        account.mojangId(UUIDs.parse(result.getString(colPremiumUuid)));
        account.bedrockId(UUIDs.parse(result.getString(colBedrockUuid)));
        account.password(result.getString(colPassword));
        account.lastIp(result.getString(colAddress));
        account.lastSeen(readMillis(result, colLastLogin));
        account.creationDate(readMillis(result, colRegisteredAt));
        account.email(result.getString(colEmail));
        account.discord(result.getString(colDiscord));
        account.settings(AccountSettings.parse(result.getString(colFlags)));
        return account;
    }

    private long readMillis(ResultSet result, String column) {
        try {
            Timestamp timestamp = result.getTimestamp(column);
            return timestamp == null ? 0L : timestamp.getTime();
        } catch (SQLException ex) {
            try {
                String raw = result.getString(column);
                return raw == null ? 0L : Long.parseLong(raw.trim());
            } catch (SQLException | NumberFormatException ignored) {
                return 0L;
            }
        }
    }
}
