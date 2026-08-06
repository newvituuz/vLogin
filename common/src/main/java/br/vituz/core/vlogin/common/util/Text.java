package br.vituz.core.vlogin.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final char COLOR_CHAR = '§';
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }

        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            StringBuilder replacement = new StringBuilder(14).append(COLOR_CHAR).append('x');
            for (char character : hex.toCharArray()) {
                replacement.append(COLOR_CHAR).append(character);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        char[] chars = buffer.toString().toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String stripColors(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)" + COLOR_CHAR + "[0-9A-FK-ORX]", "");
    }

    public static String normalizeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }
        String value = address.startsWith("/") ? address.substring(1) : address;
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            return closing == -1 ? value : value.substring(1, closing);
        }
        int colon = value.lastIndexOf(':');
        if (colon > -1 && value.indexOf(':') == colon) {
            return value.substring(0, colon);
        }
        return value;
    }
}
