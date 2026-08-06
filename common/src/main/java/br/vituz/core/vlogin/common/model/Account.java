package br.vituz.core.vlogin.common.model;

import java.util.UUID;

public final class Account {
    private long id = -1;
    private String lastName;
    private UUID uniqueId;
    private UUID mojangId;
    private UUID bedrockId;
    private String password;
    private String lastIp;
    private long lastSeen;
    private long creationDate;
    private String email;
    private String discord;
    private AccountSettings settings = new AccountSettings();

    public Account() {
    }

    public static Account create(String name, UUID uniqueId, String address) {
        Account account = new Account();
        account.lastName = name;
        account.uniqueId = uniqueId;
        account.lastIp = address;
        account.creationDate = System.currentTimeMillis();
        account.lastSeen = account.creationDate;
        return account;
    }

    public long id() {
        return id;
    }

    public void id(long id) {
        this.id = id;
    }

    public String lastName() {
        return lastName;
    }

    public void lastName(String lastName) {
        this.lastName = lastName;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public void uniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public UUID mojangId() {
        return mojangId;
    }

    public void mojangId(UUID mojangId) {
        this.mojangId = mojangId;
    }

    public UUID bedrockId() {
        return bedrockId;
    }

    public void bedrockId(UUID bedrockId) {
        this.bedrockId = bedrockId;
    }

    public String password() {
        return password;
    }

    public void password(String password) {
        this.password = password;
    }

    public String lastIp() {
        return lastIp;
    }

    public void lastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long creationDate() {
        return creationDate;
    }

    public void creationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    public String email() {
        return email;
    }

    public void email(String email) {
        this.email = email;
    }

    public String discord() {
        return discord;
    }

    public void discord(String discord) {
        this.discord = discord;
    }

    public AccountSettings settings() {
        return settings;
    }

    public void settings(AccountSettings settings) {
        this.settings = settings == null ? new AccountSettings() : settings;
    }

    public boolean isPremium() {
        return mojangId != null;
    }

    public boolean isBedrock() {
        return bedrockId != null;
    }

    public boolean isRegistered() {
        return password != null && !password.isEmpty();
    }
}
