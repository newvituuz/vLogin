package br.vituz.core.vlogin.common.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Um YAML com busca por caminho e os padrões embutidos atrás.
 *
 * O arquivo do usuário nunca é reescrito, então os comentários e a
 * formatação dele sobrevivem às atualizações.
 */
public final class YamlConfig {
    private final Map<String, Object> values;
    private final Map<String, Object> defaults;

    private YamlConfig(Map<String, Object> values, Map<String, Object> defaults) {
        this.values = values;
        this.defaults = defaults;
    }

    public static YamlConfig load(File file, InputStream defaultsStream) throws IOException {
        Map<String, Object> defaults = defaultsStream == null
                ? Collections.<String, Object>emptyMap()
                : read(new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));

        Map<String, Object> values = Collections.emptyMap();
        if (file != null && file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                values = read(reader);
            }
        }
        return new YamlConfig(values, defaults);
    }

    public static YamlConfig empty() {
        return new YamlConfig(Collections.<String, Object>emptyMap(), Collections.<String, Object>emptyMap());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Reader reader) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(Integer.MAX_VALUE);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (BufferedReader buffered = new BufferedReader(reader)) {
            Object loaded = yaml.load(buffered);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
            return Collections.emptyMap();
        }
    }

    private Object resolve(Map<String, Object> root, String path) {
        Object current = root;
        int start = 0;
        while (start <= path.length()) {
            int dot = path.indexOf('.', start);
            String key = dot == -1 ? path.substring(start) : path.substring(start, dot);
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(key);
            if (dot == -1) {
                return current;
            }
            start = dot + 1;
        }
        return current;
    }

    private Object raw(String path) {
        Object value = resolve(values, path);
        return value != null ? value : resolve(defaults, path);
    }

    public boolean contains(String path) {
        return resolve(values, path) != null;
    }

    public String getString(String path, String def) {
        Object value = raw(path);
        return value == null ? def : String.valueOf(value);
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public int getInt(String path, int def) {
        Object value = raw(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public long getLong(String path, long def) {
        Object value = raw(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = raw(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value != null) {
            String text = value.toString().trim();
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no")) {
                return false;
            }
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object value = raw(path);
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object element : (List<?>) value) {
            if (element != null) {
                result.add(String.valueOf(element));
            }
        }
        return result;
    }

    public List<String> getKeys(String path) {
        Object value = raw(path);
        if (!(value instanceof Map)) {
            return new ArrayList<>();
        }
        List<String> keys = new ArrayList<>();
        for (Object key : ((Map<?, ?>) value).keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    public Map<String, Object> getSection(String path) {
        Object value = raw(path);
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> section = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            section.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }
}
