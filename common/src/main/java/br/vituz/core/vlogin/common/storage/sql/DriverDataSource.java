package br.vituz.core.vlogin.common.storage.sql;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public final class DriverDataSource implements DataSource {
    private final Driver driver;
    private final String url;
    private final Properties properties;

    public DriverDataSource(String driverClassName, String url, Properties properties) throws SQLException {
        this.url = url;
        this.properties = properties;
        try {
            this.driver = (Driver) ourDriverClass(driverClassName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new SQLException("Unable to load JDBC driver " + driverClassName, ex);
        }
        if (!driver.acceptsURL(url)) {
            throw new SQLException("Driver " + driverClassName + " does not accept the URL " + url);
        }
    }

    /**
     * O driver que veio nesta jar, e não o que o servidor porventura tenha.
     *
     * O CraftBukkit 1.8 embute uma cópia de org.sqlite de 2010 dentro do jar dele, e o
     * class loader do Bukkit pergunta ao servidor antes de olhar o plugin. Quem atende
     * é aquele driver antigo, que nem implementa Connection.isValid e derruba o pool
     * na inicialização.
     *
     * Renomear o pacote no build não resolve: a biblioteca nativa do SQLite tem o nome
     * da classe gravado do lado do JNI e para de encontrá-la. O que resolve é carregar
     * o driver num class loader que olha esta jar primeiro.
     */
    private static Class<?> ourDriverClass(String driverClassName) throws ReflectiveOperationException {
        ClassLoader own = DriverDataSource.class.getClassLoader();
        Class<?> found = Class.forName(driverClassName, false, own);
        if (sameSource(found, DriverDataSource.class)) {
            return found;
        }
        return Class.forName(driverClassName, true, isolatedLoader(packageOf(driverClassName)));
    }

    private static boolean sameSource(Class<?> first, Class<?> second) {
        URL one = codeSource(first);
        URL other = codeSource(second);
        return one != null && one.equals(other);
    }

    private static URL codeSource(Class<?> type) {
        ProtectionDomain domain = type.getProtectionDomain();
        CodeSource source = domain == null ? null : domain.getCodeSource();
        return source == null ? null : source.getLocation();
    }

    private static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? "" : className.substring(0, lastDot + 1);
    }

    /**
     * Um class loader que procura o pacote do driver aqui dentro antes de perguntar a
     * qualquer outro, e delega o resto normalmente para não duplicar nada.
     */
    private static ClassLoader isolatedLoader(String isolatedPackage) throws ReflectiveOperationException {
        URL self = codeSource(DriverDataSource.class);
        if (self == null) {
            throw new ClassNotFoundException("Não achei a própria jar para isolar o driver.");
        }
        ClassLoader cached = ISOLATED.get(isolatedPackage);
        if (cached != null) {
            return cached;
        }
        ClassLoader created = new URLClassLoader(new URL[]{self}, DriverDataSource.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith(isolatedPackage)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
        };
        ClassLoader existing = ISOLATED.putIfAbsent(isolatedPackage, created);
        return existing != null ? existing : created;
    }

    private static final ConcurrentMap<String, ClassLoader> ISOLATED = new ConcurrentHashMap<>();

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("Driver returned no connection for " + url);
        }
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties copy = new Properties();
        copy.putAll(properties);
        if (username != null) {
            copy.setProperty("user", username);
        }
        if (password != null) {
            copy.setProperty("password", password);
        }
        Connection connection = driver.connect(url, copy);
        if (connection == null) {
            throw new SQLException("Driver returned no connection for " + url);
        }
        return connection;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + type.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }
}
