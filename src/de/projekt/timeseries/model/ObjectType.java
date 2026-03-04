package de.projekt.timeseries.model;

public enum ObjectType {

    CONTRACT_VHP(1, "Vertrag VHP"),
    CONTRACT(2, "Vertrag"),
    CONTRACT_VERANS(3, "Vertragsanschluss"),
    ANS(4, "Anschluss");

    private final int code;
    private final String description;

    ObjectType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getTypeKey() {
        return name();
    }

    public String getDescription() {
        return description;
    }

    public static ObjectType fromCode(int code) {
        for (ObjectType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unbekannter Objekttyp: " + code);
    }
}
