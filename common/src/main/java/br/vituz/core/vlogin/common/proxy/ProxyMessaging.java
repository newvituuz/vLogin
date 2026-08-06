package br.vituz.core.vlogin.common.proxy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O protocolo entre proxy e backend.
 *
 * As mensagens trafegam pela conexão do jogador, então um cliente
 * modificado consegue vê-las e reenviá-las. Cada uma leva timestamp, nonce
 * de uso único e HMAC; o que falhar em qualquer um dos três é descartado.
 */
public final class ProxyMessaging {
    public static final String CHANNEL = "vlogin:auth";
    public static final String LEGACY_CHANNEL = "vLogin";

    private static final byte VERSION = 2;
    private static final long MAX_AGE_MILLIS = 30_000L;
    private static final int MAX_REMEMBERED_NONCES = 4096;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;

    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    public ProxyMessaging(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public enum Action {
        AUTHENTICATED,
        UNAUTHENTICATED,
        LOGIN,
        LOGOUT,
        COMMAND,
        ACK;

        static Action of(String name) {
            for (Action action : values()) {
                if (action.name().equals(name)) {
                    return action;
                }
            }
            return null;
        }
    }

    public static final class Message {
        public final Action action;
        public final String player;
        public final String extra;

        public Message(Action action, String player, String extra) {
            this.action = action;
            this.player = player;
            this.extra = extra == null ? "" : extra;
        }
    }

    public byte[] encode(Action action, String player, String extra) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(body);
            data.writeByte(VERSION);
            data.writeUTF(action.name());
            data.writeUTF(player == null ? "" : player);
            data.writeUTF(extra == null ? "" : extra);
            data.writeLong(System.currentTimeMillis());
            data.writeLong(RANDOM.nextLong());
            data.writeLong(RANDOM.nextLong());

            byte[] payload = body.toByteArray();
            byte[] signature = sign(payload);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream framed = new DataOutputStream(out);
            framed.writeInt(payload.length);
            framed.write(payload);
            framed.writeInt(signature.length);
            framed.write(signature);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode a proxy message", ex);
        }
    }

    public Optional<Message> decode(byte[] input) {
        try {
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(input));
            int payloadLength = data.readInt();
            if (payloadLength <= 0 || payloadLength > input.length) {
                return Optional.empty();
            }
            byte[] payload = new byte[payloadLength];
            data.readFully(payload);

            int signatureLength = data.readInt();
            if (signatureLength <= 0 || signatureLength > 64) {
                return Optional.empty();
            }
            byte[] signature = new byte[signatureLength];
            data.readFully(signature);

            if (!MessageDigest.isEqual(signature, sign(payload))) {
                return Optional.empty();
            }

            DataInputStream body = new DataInputStream(new ByteArrayInputStream(payload));
            if (body.readByte() != VERSION) {
                return Optional.empty();
            }
            Action action = Action.of(body.readUTF());
            String player = body.readUTF();
            String extra = body.readUTF();
            long timestamp = body.readLong();
            long nonceHigh = body.readLong();
            long nonceLow = body.readLong();

            if (action == null) {
                return Optional.empty();
            }
            long now = System.currentTimeMillis();
            if (Math.abs(now - timestamp) > MAX_AGE_MILLIS) {
                return Optional.empty();
            }
            if (!claimNonce(nonceHigh + ":" + nonceLow, now)) {
                return Optional.empty();
            }
            return Optional.of(new Message(action, player, extra));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private boolean claimNonce(String nonce, long now) {
        if (seenNonces.putIfAbsent(nonce, now) != null) {
            return false;
        }
        if (seenNonces.size() > MAX_REMEMBERED_NONCES) {
            seenNonces.entrySet().removeIf(entry -> now - entry.getValue() > MAX_AGE_MILLIS);
        }
        return true;
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
