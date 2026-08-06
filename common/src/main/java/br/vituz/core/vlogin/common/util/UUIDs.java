package br.vituz.core.vlogin.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UUIDs {
    private UUIDs() {
    }

    public static String trim(UUID uuid) {
        return uuid == null ? null : uuid.toString().replace("-", "");
    }

    public static UUID parse(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.length() == 32) {
                return UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                        + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20));
            }
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static UUID offline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isBedrock(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }
}
