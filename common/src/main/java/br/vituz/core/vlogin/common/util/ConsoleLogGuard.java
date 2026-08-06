package br.vituz.core.vlogin.common.util;

import java.util.List;
import java.util.logging.Logger;

/**
 * Instala o filtro de senha só onde existe Log4j2.
 *
 * Nomear a classe do filtro já carrega o Log4j2, então a checagem precisa
 * ficar fora dela: um recurso opcional não pode impedir o plugin de subir.
 */
public final class ConsoleLogGuard {
    private static final String MARKER_CLASS = "org.apache.logging.log4j.core.filter.AbstractFilter";

    private ConsoleLogGuard() {
    }

    public static boolean install(List<String> aliases, Logger logger) {
        try {
            Class.forName(MARKER_CLASS);
        } catch (ClassNotFoundException | LinkageError ex) {
            logger.warning("Este servidor não usa Log4j2, então a senha digitada em /logar pode"
                    + " aparecer no console. Restrinja quem lê os logs.");
            return false;
        }
        try {
            return SensitiveCommandFilter.install(aliases, logger);
        } catch (Throwable ex) {
            logger.warning("Não foi possível filtrar o log de comandos (" + ex + ")."
                    + " Senhas digitadas podem aparecer no console.");
            return false;
        }
    }
}
