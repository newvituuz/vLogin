package br.vituz.core.vlogin.common.bedrock;

import java.util.UUID;

/**
 * O que se sabe sobre um jogador Bedrock.
 *
 * Vem do Floodgate quando ele está instalado; sem ele, só o UUID é conhecido.
 */
public final class BedrockIdentity {

    private final UUID uniqueId;
    private final String xuid;
    private final String gamertag;
    private final boolean linked;
    private final UUID linkedJavaId;
    private final String linkedJavaName;
    private final String deviceOs;

    public BedrockIdentity(UUID uniqueId, String xuid, String gamertag,
                           boolean linked, UUID linkedJavaId, String linkedJavaName,
                           String deviceOs) {
        this.uniqueId = uniqueId;
        this.xuid = xuid;
        this.gamertag = gamertag;
        this.linked = linked;
        this.linkedJavaId = linkedJavaId;
        this.linkedJavaName = linkedJavaName;
        this.deviceOs = deviceOs;
    }

    /** Identidade mínima, para quando só o UUID é conhecido. */
    public static BedrockIdentity ofUniqueId(UUID uniqueId) {
        return new BedrockIdentity(uniqueId, null, null, false, null, null, null);
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    /** O identificador da conta Xbox. Null quando o Floodgate não está presente. */
    public String xuid() {
        return xuid;
    }

    /** O gamertag original, que pode conter espaços. */
    public String gamertag() {
        return gamertag;
    }

    /** Se a conta Bedrock está vinculada a uma conta Java pelo Floodgate. */
    public boolean isLinked() {
        return linked;
    }

    public UUID linkedJavaId() {
        return linkedJavaId;
    }

    public String linkedJavaName() {
        return linkedJavaName;
    }

    public String deviceOs() {
        return deviceOs;
    }
}
