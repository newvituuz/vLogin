package br.vituz.core.vlogin.common.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.List;

/**
 * Tira a senha digitada do log do console.
 *
 * O servidor grava a linha do comando antes de qualquer plugin poder
 * cancelar, então o único lugar onde dá para agir é o próprio logger.
 */
public final class SensitiveCommandFilter extends AbstractFilter {
    private final SensitiveCommandMatcher matcher;

    private SensitiveCommandFilter(SensitiveCommandMatcher matcher) {
        this.matcher = matcher;
    }

    public static boolean install(List<String> aliases, java.util.logging.Logger logger) {
        SensitiveCommandMatcher matcher = new SensitiveCommandMatcher(aliases);
        if (matcher.isEmpty()) {
            return false;
        }
        try {
            org.apache.logging.log4j.Logger root = LogManager.getRootLogger();
            if (!(root instanceof Logger)) {
                logger.warning("Não foi possível filtrar o log de comandos: o servidor não usa"
                        + " Log4j2. Senhas digitadas podem aparecer no console.");
                return false;
            }
            ((Logger) root).addFilter(new SensitiveCommandFilter(matcher));
            return true;
        } catch (LinkageError | RuntimeException ex) {
            logger.warning("Não foi possível filtrar o log de comandos (" + ex + ")."
                    + " Senhas digitadas podem aparecer no console.");
            return false;
        }
    }

    @Override
    public Result filter(LogEvent event) {
        Message message = event == null ? null : event.getMessage();
        return decide(message == null ? null : message.getFormattedMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
        return decide(message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object message, Throwable thrown) {
        return decide(message == null ? null : message.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message message, Throwable thrown) {
        return decide(message == null ? null : message.getFormattedMessage());
    }

    private Result decide(String message) {
        return matcher.holdsPassword(message) ? Result.DENY : Result.NEUTRAL;
    }
}
