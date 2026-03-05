package de.projekt.timeseries.model;

public enum Currency {

    EUR(1, "EUR", "Euro"),
    USD(2, "USD", "US-Dollar"),
    GBP(3, "GBP", "Britisches Pfund"),
    CHF(4, "CHF", "Schweizer Franken"),
    DKK(5, "DKK", "Dänische Krone"),
    NOK(6, "NOK", "Norwegische Krone"),
    SEK(7, "SEK", "Schwedische Krone"),
    PLN(8, "PLN", "Polnischer Zloty"),
    CZK(9, "CZK", "Tschechische Krone");

    private final int code;
    private final String isoCode;
    private final String description;

    Currency(int code, String isoCode, String description) {
        this.code = code;
        this.isoCode = isoCode;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Currency fromCode(int code) {
        for (Currency c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unbekannte Währung: " + code);
    }
}
