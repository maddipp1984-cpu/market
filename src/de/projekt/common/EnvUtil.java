package de.projekt.common;

public final class EnvUtil {

    private EnvUtil() {}

    public static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
