package br.vituz.core.vlogin.common.bedrock;

import java.util.Optional;
import java.util.UUID;

/**
 * O reconhecimento de Bedrock sem o Floodgate.
 *
 * O Floodgate monta o UUID de um jogador Bedrock com os 8 primeiros bytes zerados,
 * e é só isso que dá para observar daqui. Serve para não perder o comportamento
 * quando o Floodgate não está instalado, mas é indício: nada impede que um UUID
 * assim chegue por outro caminho, então o login automático por este serviço só é
 * seguro num servidor onde a origem dos UUIDs é confiável.
 */
public final class HeuristicBedrockService implements BedrockService {

    private final String prefix;

    public HeuristicBedrockService(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isBedrock(UUID uniqueId) {
        return uniqueId != null && uniqueId.getMostSignificantBits() == 0L;
    }

    @Override
    public boolean looksBedrock(String username) {
        return !prefix.isEmpty() && username != null && username.startsWith(prefix);
    }

    @Override
    public Optional<BedrockIdentity> identify(UUID uniqueId) {
        return isBedrock(uniqueId)
                ? Optional.of(BedrockIdentity.ofUniqueId(uniqueId))
                : Optional.<BedrockIdentity>empty();
    }

    @Override
    public String usernamePrefix() {
        return prefix;
    }

    @Override
    public String describe() {
        return "sem Floodgate (formato do UUID)";
    }
}
