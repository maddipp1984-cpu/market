package de.market.timeseries.rest;

import java.util.Arrays;

public final class EnumParser {

    private EnumParser() {}

    public static <E extends Enum<E>> E parse(Class<E> enumClass, String value, String fieldName) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Ungueltiger Wert fuer '" + fieldName + "': " + value
                    + ". Erlaubt: " + Arrays.toString(enumClass.getEnumConstants()));
        }
    }
}
