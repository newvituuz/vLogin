package br.vituz.core.vlogin.bukkit.compat;

import org.bukkit.Bukkit;

public final class ServerVersion {
    private static final int MAJOR;
    private static final int MINOR;
    private static final int PATCH;

    static {
        int major = 1;
        int minor = 8;
        int patch = 0;
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            major = Integer.parseInt(parts[0]);
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1]);
            }
            patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (RuntimeException ignored) {
        }
        MAJOR = major;
        MINOR = minor;
        PATCH = patch;
    }

    private ServerVersion() {
    }

    public static int minor() {
        return MINOR;
    }

    public static int patch() {
        return PATCH;
    }

    public static boolean atLeast(int minor) {
        return MAJOR > 1 || MINOR >= minor;
    }

    public static boolean atLeast(int minor, int patch) {
        if (MAJOR > 1) {
            return true;
        }
        return MINOR > minor || (MINOR == minor && PATCH >= patch);
    }

    public static String display() {
        return MAJOR + "." + MINOR + (PATCH > 0 ? "." + PATCH : "");
    }

    public static String nmsPackage() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        int index = name.lastIndexOf('.');
        String fragment = index == -1 ? "" : name.substring(index + 1);
        return fragment.startsWith("v1_") ? fragment : null;
    }
}
