package de.projekt.timeseries.model;

public enum Unit {

    // Energie (1-9)
    KWH(1, "kWh", "Kilowattstunde"),
    MWH(2, "MWh", "Megawattstunde"),
    GWH(3, "GWh", "Gigawattstunde"),
    KJ(4, "kJ", "Kilojoule"),
    MJ(5, "MJ", "Megajoule"),
    GJ(6, "GJ", "Gigajoule"),

    // Leistung (10-19)
    W(10, "W", "Watt"),
    KW(11, "kW", "Kilowatt"),
    MW(12, "MW", "Megawatt"),
    GW(13, "GW", "Gigawatt"),
    KVA(14, "kVA", "Kilovoltampere"),
    MVA(15, "MVA", "Megavoltampere"),
    KVAR(16, "kvar", "Kilovar"),
    MVAR(17, "Mvar", "Megavar"),

    // Gas - Volumen (20-29)
    M3(20, "m³", "Kubikmeter"),
    NM3(21, "Nm³", "Normkubikmeter"),
    TM3(22, "Tm³", "Tausend Kubikmeter"),

    // Temperatur / Druck / Physik (30-39)
    CELSIUS(30, "°C", "Grad Celsius"),
    KELVIN(31, "K", "Kelvin"),
    BAR(32, "bar", "Bar"),
    MBAR(33, "mbar", "Millibar"),
    PERCENT(34, "%", "Prozent"),

    // 40-49: reserviert

    // Mengen / Sonstiges (50-59)
    TONNE(50, "t", "Tonne"),
    KG(51, "kg", "Kilogramm"),
    TONNE_CO2(52, "t CO₂", "Tonne CO₂"),
    HOURS(53, "h", "Stunden"),
    NONE(54, "", "Dimensionslos");

    private final int code;
    private final String symbol;
    private final String description;

    Unit(int code, String symbol, String description) {
        this.code = code;
        this.symbol = symbol;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }

    public static Unit fromCode(int code) {
        for (Unit u : values()) {
            if (u.code == code) {
                return u;
            }
        }
        throw new IllegalArgumentException("Unbekannte Unit: " + code);
    }
}
