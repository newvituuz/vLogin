package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class VelocityConsoleLogger {
    private VelocityConsoleLogger() {
    }

    static Logger create(ProxyServer server) {
        Logger logger = Logger.getLogger("vLogin");
        logger.setUseParentHandlers(false);
        for (Handler existing : logger.getHandlers()) {
            logger.removeHandler(existing);
        }
        logger.addHandler(new ConsoleHandler(server));
        logger.setLevel(Level.ALL);
        return logger;
    }

    private static final class ConsoleHandler extends Handler {
        private final ProxyServer server;

        ConsoleHandler(ProxyServer server) {
            this.server = server;
        }

        @Override
        public void publish(LogRecord record) {
            if (record == null) {
                return;
            }
            String message = record.getMessage();
            if (record.getParameters() != null && record.getParameters().length > 0) {
                try {
                    message = java.text.MessageFormat.format(message, record.getParameters());
                } catch (IllegalArgumentException ignored) {
                }
            }

            NamedTextColor color = NamedTextColor.GRAY;
            int level = record.getLevel().intValue();
            if (level >= Level.SEVERE.intValue()) {
                color = NamedTextColor.RED;
            } else if (level >= Level.WARNING.intValue()) {
                color = NamedTextColor.YELLOW;
            }

            server.getConsoleCommandSource().sendMessage(
                    Component.text("[vLogin] " + message).color(color));

            if (record.getThrown() != null) {
                record.getThrown().printStackTrace();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
