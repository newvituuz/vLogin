package br.vituz.core.vlogin.common.storage.sql;

import java.util.Locale;

public enum SqlDialect {
    SQLITE("org.sqlite.JDBC"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver");

    private final String driverClass;

    SqlDialect(String driverClass) {
        this.driverClass = driverClass;
    }

    public String driverClass() {
        return driverClass;
    }

    public boolean isRemote() {
        return this != SQLITE;
    }

    public String autoIncrementColumn() {
        return this == SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT NOT NULL AUTO_INCREMENT PRIMARY KEY";
    }

    public static SqlDialect parse(String value) {
        if (value == null) {
            return SQLITE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "MYSQL":
                return MYSQL;
            case "MARIADB":
                return MARIADB;
            default:
                return SQLITE;
        }
    }
}
