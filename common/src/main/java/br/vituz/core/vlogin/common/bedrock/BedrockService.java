package br.vituz.core.vlogin.common.bedrock;

import java.util.Optional;
import java.util.UUID;

/**
 * Como o plugin reconhece um jogador Bedrock.
 *
 * Com o Floodgate instalado, a resposta vem dele e é confiável. Sem o Floodgate,
 * sobra o formato do UUID, que é indício e não prova.
 */
public interface BedrockService {

    /** Se o Floodgate está presente e respondendo. */
    boolean isAvailable();

    boolean isBedrock(UUID uniqueId);

    /**
     * Reconhece um nickname como Bedrock pelo prefixo que o Floodgate adiciona.
     *
     * Usado no pré-login, onde nem toda plataforma já sabe o UUID da conexão.
     */
    boolean looksBedrock(String username);

    Optional<BedrockIdentity> identify(UUID uniqueId);

    /** O prefixo que o Floodgate coloca nos nicknames, normalmente ".". */
    String usernamePrefix();

    /** Nome da origem, para o diagnóstico do /vlogin support. */
    String describe();
}
