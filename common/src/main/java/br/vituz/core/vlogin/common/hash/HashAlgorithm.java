package br.vituz.core.vlogin.common.hash;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Os algoritmos suportados, e a detecção de qual deles gerou um hash.
 */
public enum HashAlgorithm {
    MD5,
    SHA256,
    SHA512,
    PBKDF2,
    BCRYPT2A,
    BCRYPT2Y,
    BCRYPT2B,
    ARGON2I,
    ARGON2D,
    ARGON2ID;

    public boolean isBcrypt() {
        return this == BCRYPT2A || this == BCRYPT2Y || this == BCRYPT2B;
    }

    public boolean isArgon2() {
        return this == ARGON2I || this == ARGON2D || this == ARGON2ID;
    }

    public String bcryptVersion() {
        switch (this) {
            case BCRYPT2Y:
                return "2y";
            case BCRYPT2B:
                return "2b";
            default:
                return "2a";
        }
    }

    public String argon2Name() {
        switch (this) {
            case ARGON2I:
                return "argon2i";
            case ARGON2D:
                return "argon2d";
            default:
                return "argon2id";
        }
    }

    public static HashAlgorithm parse(String value, HashAlgorithm fallback, Logger logger) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (HashAlgorithm algorithm : values()) {
            if (algorithm.name().equals(normalized)) {
                return algorithm;
            }
        }
        if (normalized.equals("BCRYPT")) {
            return BCRYPT2A;
        }
        if (normalized.equals("ARGON2")) {
            return ARGON2ID;
        }
        if (normalized.equals("SHA1")) {
            logger.warning("SHA1 is not supported for new passwords; using SHA512 instead.");
            return SHA512;
        }
        if (logger != null) {
            logger.warning("Unknown hashing algorithm '" + value + "', using " + fallback + ".");
        }
        return fallback;
    }

    public static HashAlgorithm identify(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        if (encoded.startsWith("$2a$")) {
            return BCRYPT2A;
        }
        if (encoded.startsWith("$2y$")) {
            return BCRYPT2Y;
        }
        if (encoded.startsWith("$2b$") || encoded.startsWith("$2$")) {
            return BCRYPT2B;
        }
        if (encoded.startsWith("$argon2id$")) {
            return ARGON2ID;
        }
        if (encoded.startsWith("$argon2i$")) {
            return ARGON2I;
        }
        if (encoded.startsWith("$argon2d$")) {
            return ARGON2D;
        }
        if (encoded.startsWith("$PBKDF2$")) {
            return PBKDF2;
        }
        if (encoded.startsWith("$SHA256$")) {
            return SHA256;
        }
        if (encoded.startsWith("$SHA512$")) {
            return SHA512;
        }
        if (encoded.startsWith("$MD5$")) {
            return MD5;
        }
        if (isHex(encoded)) {
            switch (encoded.length()) {
                case 32:
                    return MD5;
                case 64:
                    return SHA256;
                case 128:
                    return SHA512;
                default:
                    return null;
            }
        }
        return null;
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
