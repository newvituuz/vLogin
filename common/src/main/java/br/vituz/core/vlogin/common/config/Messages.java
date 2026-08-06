package br.vituz.core.vlogin.common.config;

import br.vituz.core.vlogin.common.util.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Messages {
    private final Map<MessageKey, String> cache = new EnumMap<>(MessageKey.class);
    private final String prefix;

    public Messages(YamlConfig language) {
        for (MessageKey key : MessageKey.values()) {
            cache.put(key, language.getString(key.path(), key.fallback()));
        }
        this.prefix = cache.get(MessageKey.PREFIX);
    }

    public String get(MessageKey key, Object... placeholders) {
        return Text.colorize(prefixed(raw(key, placeholders)));
    }

    public String plain(MessageKey key, Object... placeholders) {
        return Text.colorize(raw(key, placeholders));
    }

    public List<String> lines(MessageKey key, Object... placeholders) {
        String[] parts = raw(key, placeholders).split("\n");
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(Text.colorize(part));
        }
        return result;
    }

    private String raw(MessageKey key, Object... placeholders) {
        String message = cache.get(key);
        if (message == null) {
            message = key.fallback();
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return message;
    }

    private String prefixed(String message) {
        if (prefix == null || prefix.isEmpty() || message.isEmpty()) {
            return message;
        }
        return prefix + message;
    }

    public String prefix() {
        return Text.colorize(prefix);
    }
}
