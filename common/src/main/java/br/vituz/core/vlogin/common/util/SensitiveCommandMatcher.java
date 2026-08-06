package br.vituz.core.vlogin.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decide se uma linha de log está prestes a publicar uma senha.
 *
 * Um /logar sem argumento continua no log, porque o histórico de quem tentou
 * entrar é útil; o que some é o segredo que vinha depois.
 */
public final class SensitiveCommandMatcher {
    private final List<String> commands = new ArrayList<>();

    public SensitiveCommandMatcher(List<String> aliases) {
        for (String alias : aliases) {
            if (alias == null || alias.trim().isEmpty()) {
                continue;
            }
            String lower = alias.trim().toLowerCase(Locale.ROOT);
            String slashed = lower.startsWith("/") ? lower : "/" + lower;
            commands.add(slashed);
            commands.add("/vlogin:" + slashed.substring(1));
            commands.add("/minecraft:" + slashed.substring(1));
        }
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public boolean holdsPassword(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.contains("command")) {
            return false;
        }
        for (String command : commands) {
            int index = lower.indexOf(command);
            while (index >= 0) {
                int after = index + command.length();
                boolean argumentsFollow = after < lower.length()
                        && lower.charAt(after) == ' '
                        && after + 1 < lower.length();
                if (argumentsFollow) {
                    return true;
                }
                index = lower.indexOf(command, index + 1);
            }
        }
        return false;
    }
}
