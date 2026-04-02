package de.market.seriestype.model;

public enum SeriesCategory {
    FINANCIAL(1, "Finanziell"),
    PHYSICAL(2, "Physikalisch");

    private final int code;
    private final String label;

    SeriesCategory(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public static SeriesCategory fromCode(int code) {
        for (SeriesCategory c : values()) {
            if (c.code == code) return c;
        }
        throw new IllegalArgumentException("Unbekannte Kategorie: " + code);
    }
}
