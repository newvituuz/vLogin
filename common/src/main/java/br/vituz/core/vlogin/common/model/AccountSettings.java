package br.vituz.core.vlogin.common.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Os flags da conta, gravados numa coluna só como {@code chave=valor;chave=valor}.
 *
 * Pedaços que não seguem esse formato são guardados intactos e devolvidos na
 * gravação. Isso importa quando a tabela é compartilhada com outro plugin de
 * login: o nLogin, por exemplo, guarda um JSON nessa mesma coluna, e sem essa
 * preservação a primeira gravação do vLogin apagaria os dados dele.
 */
public final class AccountSettings {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> foreign = new ArrayList<>();

    public static AccountSettings parse(String raw) {
        AccountSettings settings = new AccountSettings();
        if (raw == null || raw.isEmpty()) {
            return settings;
        }
        for (String entry : raw.split(";")) {
            int equals = entry.indexOf('=');
            if (equals > 0) {
                settings.values.put(entry.substring(0, equals).trim(), entry.substring(equals + 1).trim());
            } else if (!entry.trim().isEmpty()) {
                settings.foreign.add(entry);
            }
        }
        return settings;
    }

    public String serialize() {
        if (values.isEmpty() && foreign.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        for (String entry : foreign) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry);
        }
        return builder.toString();
    }

    public boolean getBoolean(String key, boolean def) {
        String value = values.get(key);
        return value == null ? def : Boolean.parseBoolean(value);
    }

    public void setBoolean(String key, boolean value) {
        values.put(key, Boolean.toString(value));
    }

    public long getLong(String key, long def) {
        String value = values.get(key);
        if (value == null) {
            return def;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public void setLong(String key, long value) {
        values.put(key, Long.toString(value));
    }

    public String getString(String key, String def) {
        String value = values.get(key);
        return value == null ? def : value;
    }

    public void setString(String key, String value) {
        if (value == null) {
            values.remove(key);
        } else {
            // O ';' separa as entradas, então um valor que o contenha viraria duas na
            // próxima leitura. O '=' não precisa de tratamento: o parse corta na
            // primeira ocorrência, e o resto fica inteiro no valor.
            values.put(key, value.replace(';', ' '));
        }
    }

    public static final String PREMIUM_QUESTION_ANSWERED = "premium-asked";

    public static final String SESSION_STARTED = "session";

    public static final String SESSION_ADDRESS = "session-ip";

    /** O identificador da conta Xbox, quando o Floodgate informa. */
    public static final String BEDROCK_XUID = "xuid";

    /** O gamertag original, que pode diferir do nickname usado no servidor. */
    public static final String BEDROCK_GAMERTAG = "gamertag";
}
