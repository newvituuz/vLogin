package br.vituz.core.vlogin.common.bedrock;

import java.util.logging.Logger;

/**
 * Escolhe como reconhecer jogadores Bedrock.
 *
 * O Floodgate é opcional. Nomear a classe que o usa já obrigaria o carregamento das
 * classes dele, e o erro apareceria no ponto da chamada, fora de qualquer try/catch
 * interno. Por isso a checagem mora aqui, sem nenhuma referência ao Floodgate no
 * corpo do método, e qualquer falha vira "sem Floodgate" em vez de impedir o plugin
 * de subir.
 */
public final class BedrockSupport {

    private static final String MARKER_CLASS = "org.geysermc.floodgate.api.FloodgateApi";

    private BedrockSupport() {
    }

    /**
     * @param useFloodgate se a integração está ligada no config
     * @param fallbackPrefix prefixo do config, usado se o Floodgate não informar o dele
     */
    public static BedrockService detect(boolean useFloodgate, String fallbackPrefix, Logger logger) {
        if (!useFloodgate) {
            logger.info("Integração com o Floodgate desligada no config;"
                    + " jogadores Bedrock serão reconhecidos pelo formato do UUID.");
            return new HeuristicBedrockService(fallbackPrefix);
        }

        try {
            Class.forName(MARKER_CLASS);
        } catch (ClassNotFoundException | LinkageError ex) {
            logger.info("Floodgate não encontrado; jogadores Bedrock serão reconhecidos"
                    + " pelo formato do UUID.");
            return new HeuristicBedrockService(fallbackPrefix);
        }

        try {
            BedrockService service = create(fallbackPrefix);
            // A API pode ainda não responder se o vLogin subiu antes do Floodgate; o
            // serviço busca de novo a cada uso, então isso se resolve sozinho.
            logger.info(service.isAvailable()
                    ? "Floodgate detectado: contas Bedrock serão identificadas por ele."
                    : "Floodgate encontrado, mas ainda não iniciado; a integração passa a"
                    + " valer assim que ele subir.");
            return service;
        } catch (Throwable ex) {
            logger.warning("Floodgate presente mas não pôde ser usado (" + ex + ");"
                    + " voltando ao reconhecimento pelo formato do UUID.");
        }
        return new HeuristicBedrockService(fallbackPrefix);
    }

    /**
     * Separado para que a classe que referencia o Floodgate só seja carregada depois
     * da checagem acima.
     */
    private static BedrockService create(String fallbackPrefix) {
        return new FloodgateBedrockService(fallbackPrefix);
    }
}
