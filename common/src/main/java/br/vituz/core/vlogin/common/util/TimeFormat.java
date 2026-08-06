package br.vituz.core.vlogin.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TimeFormat {
    private TimeFormat() {
    }

    public static String duration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append('d');
        }
        if (hours > 0) {
            append(text).append(hours).append('h');
        }
        if (minutes > 0 && days == 0) {
            append(text).append(minutes).append('m');
        }
        if (text.length() == 0) {
            text.append(seconds).append('s');
        }
        return text.toString();
    }

    private static StringBuilder append(StringBuilder text) {
        if (text.length() > 0) {
            text.append(' ');
        }
        return text;
    }

    public static String relative(long timestamp, String prefix) {
        if (timestamp <= 0) {
            return "";
        }
        return prefix + " " + duration(System.currentTimeMillis() - timestamp);
    }

    public static String absolute(long timestamp, String separator) {
        if (timestamp <= 0) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy '" + separator + "' HH:mm:ss", Locale.ROOT);
        return format.format(new Date(timestamp));
    }

    public static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0d);
    }
}
