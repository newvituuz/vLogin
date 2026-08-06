package br.vituz.core.vlogin.common.hash;

import br.vituz.core.vlogin.common.config.Settings;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;

/**
 * Gera e confere senhas.
 *
 * Senhas novas usam o algoritmo do config, mas a conferência é guiada pelo
 * prefixo do hash guardado, então um banco escrito por outro plugin de
 * login continua funcionando. Um hash em formato antigo é regravado no
 * formato atual no primeiro login bem-sucedido.
 */
public final class PasswordHashing {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_LENGTH = 16;

    private final Settings settings;

    public PasswordHashing(Settings settings) {
        this.settings = settings;
    }

    public String hash(String password) {
        return hash(password, settings.algorithm);
    }

    public String hash(String password, HashAlgorithm algorithm) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        if (algorithm.isBcrypt()) {
            return OpenBSDBCrypt.generate(algorithm.bcryptVersion(), password.toCharArray(), salt,
                    settings.bcryptCost);
        }
        if (algorithm.isArgon2()) {
            return argon2(password, salt, algorithm, settings.argon2Iterations,
                    settings.argon2MemoryMb * 1024, settings.argon2Parallelism);
        }
        switch (algorithm) {
            case PBKDF2:
                return "$PBKDF2$" + settings.pbkdf2Mac + "$" + settings.pbkdf2Iterations + "$"
                        + base64(salt) + "$" + base64(pbkdf2(password, salt, settings.pbkdf2Iterations,
                        settings.pbkdf2Mac));
            case SHA512:
                return "$SHA512$" + base64(salt) + "$" + base64(digest("SHA-512", password, salt));
            case SHA256:
                return "$SHA256$" + base64(salt) + "$" + base64(digest("SHA-256", password, salt));
            case MD5:
            default:
                return "$MD5$" + base64(salt) + "$" + base64(digest("MD5", password, salt));
        }
    }

    public boolean verify(String password, String encoded) {
        if (password == null || encoded == null || encoded.isEmpty()) {
            return false;
        }
        HashAlgorithm algorithm = HashAlgorithm.identify(encoded);
        if (algorithm == null) {
            return false;
        }

        try {
            if (algorithm.isBcrypt()) {
                return OpenBSDBCrypt.checkPassword(encoded, password.toCharArray());
            }
            if (algorithm.isArgon2()) {
                return verifyArgon2(password, encoded);
            }
            switch (algorithm) {
                case PBKDF2:
                    return verifyPbkdf2(password, encoded);
                case SHA512:
                    return verifySalted(password, encoded, "SHA-512", 128);
                case SHA256:
                    return verifySalted(password, encoded, "SHA-256", 64);
                case MD5:
                    return verifySalted(password, encoded, "MD5", 32);
                default:
                    return false;
            }
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean needsRehash(String encoded) {
        HashAlgorithm current = HashAlgorithm.identify(encoded);
        if (current == null) {
            return true;
        }
        if (current != settings.algorithm) {
            return true;
        }
        if (current.isBcrypt()) {
            int start = encoded.indexOf('$', 1) + 1;
            int end = encoded.indexOf('$', start);
            if (start > 0 && end > start) {
                try {
                    return Integer.parseInt(encoded.substring(start, end)) != settings.bcryptCost;
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private String argon2(String password, byte[] salt, HashAlgorithm algorithm, int iterations,
                          int memoryKb, int parallelism) {
        byte[] output = new byte[32];
        Argon2Parameters parameters = new Argon2Parameters.Builder(argon2Type(algorithm))
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password.toCharArray(), output);

        return "$" + algorithm.argon2Name() + "$v=19$m=" + memoryKb + ",t=" + iterations + ",p=" + parallelism
                + "$" + base64(salt) + "$" + base64(output);
    }

    private boolean verifyArgon2(String password, String encoded) {
        String[] parts = encoded.split("\\$");
        if (parts.length < 6) {
            return false;
        }
        HashAlgorithm algorithm = HashAlgorithm.identify(encoded);
        int version = Argon2Parameters.ARGON2_VERSION_13;
        int index = 2;
        if (parts[index].startsWith("v=")) {
            version = Integer.parseInt(parts[index].substring(2));
            index++;
        }

        int memoryKb = 0;
        int iterations = 0;
        int parallelism = 1;
        for (String option : parts[index].split(",")) {
            int equals = option.indexOf('=');
            if (equals == -1) {
                continue;
            }
            String name = option.substring(0, equals);
            int value = Integer.parseInt(option.substring(equals + 1));
            if (name.equals("m")) {
                memoryKb = value;
            } else if (name.equals("t")) {
                iterations = value;
            } else if (name.equals("p")) {
                parallelism = value;
            }
        }
        index++;

        byte[] salt = decodeBase64(parts[index++]);
        byte[] expected = decodeBase64(parts[index]);
        if (salt.length == 0 || expected.length == 0 || memoryKb <= 0 || iterations <= 0) {
            return false;
        }

        byte[] actual = new byte[expected.length];
        Argon2Parameters parameters = new Argon2Parameters.Builder(argon2Type(algorithm))
                .withVersion(version)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password.toCharArray(), actual);

        return MessageDigest.isEqual(expected, actual);
    }

    private static int argon2Type(HashAlgorithm algorithm) {
        if (algorithm == HashAlgorithm.ARGON2I) {
            return Argon2Parameters.ARGON2_i;
        }
        if (algorithm == HashAlgorithm.ARGON2D) {
            return Argon2Parameters.ARGON2_d;
        }
        return Argon2Parameters.ARGON2_id;
    }

    private boolean verifyPbkdf2(String password, String encoded) {
        String[] parts = encoded.split("\\$");
        if (parts.length < 6) {
            return false;
        }
        String algorithm = parts[2];
        int iterations = Integer.parseInt(parts[3]);
        byte[] salt = decodeBase64(parts[4]);
        byte[] expected = decodeBase64(parts[5]);
        byte[] actual = pbkdf2(password, salt, iterations, algorithm, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, String algorithm) {
        return pbkdf2(password, salt, iterations, algorithm, 512);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, String algorithm, int keyLengthBits) {
        try {
            String factoryName = algorithm.toUpperCase(Locale.ROOT).startsWith("PBKDF2")
                    ? algorithm
                    : "PBKDF2With" + algorithm;
            SecretKeyFactory factory = SecretKeyFactory.getInstance(factoryName);
            return factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits))
                    .getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 algorithm unavailable: " + algorithm, ex);
        }
    }

    private boolean verifySalted(String password, String encoded, String digestName, int bareHexLength) {
        if (encoded.length() == bareHexLength && encoded.indexOf('$') == -1) {
            byte[] expected = hexToBytes(encoded);
            return MessageDigest.isEqual(expected, digest(digestName, password, new byte[0]));
        }
        String[] parts = encoded.split("\\$");
        if (parts.length < 4) {
            return false;
        }
        byte[] salt = decodeBase64(parts[2]);
        byte[] expected = decodeBase64(parts[3]);
        return MessageDigest.isEqual(expected, digest(digestName, password, salt));
    }

    private static byte[] digest(String algorithm, String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Digest algorithm unavailable: " + algorithm, ex);
        }
    }

    private static String base64(byte[] input) {
        return Base64.getEncoder().withoutPadding().encodeToString(input);
    }

    private static byte[] decodeBase64(String input) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    private static byte[] hexToBytes(String input) {
        int length = input.length();
        byte[] output = new byte[length / 2];
        for (int i = 0; i < length - 1; i += 2) {
            output[i / 2] = (byte) ((Character.digit(input.charAt(i), 16) << 4)
                    + Character.digit(input.charAt(i + 1), 16));
        }
        return output;
    }
}
