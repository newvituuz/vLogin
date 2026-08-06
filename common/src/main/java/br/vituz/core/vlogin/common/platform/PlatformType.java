package br.vituz.core.vlogin.common.platform;

public enum PlatformType {
    BUKKIT("Bukkit", false),
    BUNGEE("BungeeCord", true),
    VELOCITY("Velocity", true);

    private final String displayName;
    private final boolean proxy;

    PlatformType(String displayName, boolean proxy) {
        this.displayName = displayName;
        this.proxy = proxy;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isProxy() {
        return proxy;
    }
}
