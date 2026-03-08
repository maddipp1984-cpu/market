package de.market.timeseries.model;

public enum Unit {

    // Energie (1-9)
    KWH(1, "kWh", "Kilowattstunde", UnitCategory.ENERGY, 1.0),
    MWH(2, "MWh", "Megawattstunde", UnitCategory.ENERGY, 1000.0),
    GWH(3, "GWh", "Gigawattstunde", UnitCategory.ENERGY, 1_000_000.0),
    KJ(4, "kJ", "Kilojoule", UnitCategory.ENERGY, 1.0 / 3600.0),
    MJ(5, "MJ", "Megajoule", UnitCategory.ENERGY, 1.0 / 3.6),
    GJ(6, "GJ", "Gigajoule", UnitCategory.ENERGY, 1000.0 / 3.6),

    // Wirkleistung (10-13)
    W(10, "W", "Watt", UnitCategory.ACTIVE_POWER, 0.001),
    KW(11, "kW", "Kilowatt", UnitCategory.ACTIVE_POWER, 1.0),
    MW(12, "MW", "Megawatt", UnitCategory.ACTIVE_POWER, 1000.0),
    GW(13, "GW", "Gigawatt", UnitCategory.ACTIVE_POWER, 1_000_000.0),

    // Scheinleistung (14-15)
    KVA(14, "kVA", "Kilovoltampere", UnitCategory.APPARENT_POWER, 1.0),
    MVA(15, "MVA", "Megavoltampere", UnitCategory.APPARENT_POWER, 1000.0),

    // Blindleistung (16-17)
    KVAR(16, "kvar", "Kilovar", UnitCategory.REACTIVE_POWER, 1.0),
    MVAR(17, "Mvar", "Megavar", UnitCategory.REACTIVE_POWER, 1000.0),

    // Gas - Volumen (20-22)
    M3(20, "m³", "Kubikmeter", UnitCategory.GAS_VOLUME, 1.0),
    NM3(21, "Nm³", "Normkubikmeter", UnitCategory.GAS_NORM_VOLUME, 1.0),
    TM3(22, "Tm³", "Tausend Kubikmeter", UnitCategory.GAS_VOLUME, 1000.0),

    // Temperatur (30-31)
    CELSIUS(30, "°C", "Grad Celsius", UnitCategory.TEMPERATURE, 1.0),
    KELVIN(31, "K", "Kelvin", UnitCategory.TEMPERATURE, 1.0),

    // Druck (32-33)
    BAR(32, "bar", "Bar", UnitCategory.PRESSURE, 1.0),
    MBAR(33, "mbar", "Millibar", UnitCategory.PRESSURE, 0.001),

    // Prozent (34)
    PERCENT(34, "%", "Prozent", UnitCategory.NONE, 1.0),

    // Mengen / Sonstiges (50-54)
    TONNE(50, "t", "Tonne", UnitCategory.MASS, 1000.0),
    KG(51, "kg", "Kilogramm", UnitCategory.MASS, 1.0),
    TONNE_CO2(52, "t CO₂", "Tonne CO₂", UnitCategory.NONE, 1.0),
    HOURS(53, "h", "Stunden", UnitCategory.NONE, 1.0),
    NONE(54, "", "Dimensionslos", UnitCategory.NONE, 1.0);

    private final int code;
    private final String symbol;
    private final String description;
    private final UnitCategory category;
    private final double toBaseFactor;

    Unit(int code, String symbol, String description, UnitCategory category, double toBaseFactor) {
        this.code = code;
        this.symbol = symbol;
        this.description = description;
        this.category = category;
        this.toBaseFactor = toBaseFactor;
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

    public UnitCategory getCategory() {
        return category;
    }

    public double getToBaseFactor() {
        return toBaseFactor;
    }

    /**
     * Prüft ob eine direkte Konvertierung (Faktor oder Offset) möglich ist.
     */
    public boolean isConvertibleTo(Unit target) {
        if (this == target) return true;
        if (category == UnitCategory.NONE || target.category == UnitCategory.NONE) return false;
        return category == target.category;
    }

    /**
     * Prüft ob eine Cross-Domain-Konvertierung (Power↔Energy) möglich ist.
     */
    public boolean isCrossDomainConvertibleTo(Unit target) {
        return (category == UnitCategory.ACTIVE_POWER && target.category == UnitCategory.ENERGY)
            || (category == UnitCategory.ENERGY && target.category == UnitCategory.ACTIVE_POWER);
    }

    public static Unit fromCode(int code) {
        for (Unit u : values()) {
            if (u.code == code) {
                return u;
            }
        }
        throw new IllegalArgumentException("Unbekannte Unit: " + code);
    }

    // ================================================================
    // UnitCategory
    // ================================================================

    public enum UnitCategory {
        ENERGY(false),
        ACTIVE_POWER(false),
        APPARENT_POWER(false),
        REACTIVE_POWER(false),
        GAS_VOLUME(false),
        GAS_NORM_VOLUME(false),
        TEMPERATURE(true),
        PRESSURE(false),
        MASS(false),
        NONE(false);

        private final boolean offset;

        UnitCategory(boolean offset) {
            this.offset = offset;
        }

        public boolean isOffset() {
            return offset;
        }
    }
}
