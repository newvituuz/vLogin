package br.vituz.core.vlogin.common.bedrock;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.LinkedPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * O reconhecimento de Bedrock pelo Floodgate.
 *
 * Aqui a resposta é autoritativa: o Floodgate autenticou a conta Xbox antes de o
 * jogador chegar, e entrega o XUID, o gamertag e o vínculo com a conta Java. É a
 * diferença entre saber que alguém é Bedrock e apenas achar pelo formato do UUID.
 *
 * A API é buscada a cada uso até responder, e não uma vez no boot: se o vLogin subir
 * antes do Floodgate, getInstance() ainda devolve null, e prender essa resposta
 * deixaria o servidor na heurística para sempre. Enquanto ela não vem, as perguntas
 * caem na heurística, nunca num "não é Bedrock" silencioso.
 *
 * A classe só é carregada depois que {@link BedrockSupport} confirma que o Floodgate
 * existe: nomeá-la já traz as classes dele junto.
 */
final class FloodgateBedrockService implements BedrockService {

    private final HeuristicBedrockService fallback;
    private final String configuredPrefix;

    private volatile FloodgateApi api;
    private volatile String prefix;

    FloodgateBedrockService(String configuredPrefix) {
        this.configuredPrefix = configuredPrefix == null ? "" : configuredPrefix;
        this.fallback = new HeuristicBedrockService(this.configuredPrefix);
        this.prefix = this.configuredPrefix;
    }

    /**
     * @return a API, ou null enquanto o Floodgate não terminou de subir
     */
    private FloodgateApi api() {
        FloodgateApi current = api;
        if (current != null) {
            return current;
        }
        try {
            current = FloodgateApi.getInstance();
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
        if (current == null) {
            return null;
        }
        api = current;

        try {
            String reported = current.getPlayerPrefix();
            if (reported != null && !reported.isEmpty()) {
                // O prefixo do Floodgate vence o do config: é ele quem monta o nickname.
                prefix = reported;
            }
        } catch (RuntimeException ignored) {
            // Versões antigas podem não expor o prefixo; o do config resolve.
        }
        return current;
    }

    @Override
    public boolean isAvailable() {
        return api() != null;
    }

    @Override
    public boolean isBedrock(UUID uniqueId) {
        if (uniqueId == null) {
            return false;
        }
        FloodgateApi current = api();
        if (current == null) {
            return fallback.isBedrock(uniqueId);
        }
        try {
            return current.isFloodgatePlayer(uniqueId);
        } catch (RuntimeException ex) {
            return fallback.isBedrock(uniqueId);
        }
    }

    @Override
    public boolean looksBedrock(String username) {
        api();
        return !prefix.isEmpty() && username != null && username.startsWith(prefix);
    }

    @Override
    public Optional<BedrockIdentity> identify(UUID uniqueId) {
        if (uniqueId == null) {
            return Optional.empty();
        }
        FloodgateApi current = api();
        if (current == null) {
            return fallback.identify(uniqueId);
        }

        try {
            FloodgatePlayer player = current.getPlayer(uniqueId);
            if (player == null) {
                return fallback.identify(uniqueId);
            }

            boolean linked = player.isLinked();
            UUID linkedId = null;
            String linkedName = null;
            if (linked) {
                LinkedPlayer link = player.getLinkedPlayer();
                if (link != null) {
                    linkedId = link.getJavaUniqueId();
                    linkedName = link.getJavaUsername();
                }
            }

            return Optional.of(new BedrockIdentity(
                    player.getCorrectUniqueId(),
                    player.getXuid(),
                    player.getUsername(),
                    linked,
                    linkedId,
                    linkedName,
                    player.getDeviceOs() == null ? null : player.getDeviceOs().toString()));
        } catch (RuntimeException ex) {
            return fallback.identify(uniqueId);
        }
    }

    @Override
    public String usernamePrefix() {
        api();
        return prefix;
    }

    @Override
    public String describe() {
        return api() == null
                ? "Floodgate presente mas ainda não iniciado (usando o formato do UUID)"
                : "Floodgate (prefixo \"" + prefix + "\")";
    }
}
