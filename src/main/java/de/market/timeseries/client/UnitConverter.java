package de.market.timeseries.client;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/**
 * Konvertiert Werte zwischen Units (Faktor, Offset, Power↔Energy).
 * Package-private, wird vom TimeSeriesClient orchestriert.
 */
class UnitConverter {

    private static final double CELSIUS_TO_KELVIN_OFFSET = 273.15;

    /**
     * Faktor- oder Offset-Konvertierung (gleiche Kategorie).
     */
    static TimeSeriesSlice convert(TimeSeriesSlice slice, Unit source, Unit target) {
        if (source == target) return slice;
        validateSameCategory(source, target);

        double[] src = slice.getValues();
        double[] result = new double[src.length];

        if (source.getCategory().isOffset()) {
            double offset = getOffset(source, target);
            for (int i = 0; i < src.length; i++) {
                result[i] = Double.isNaN(src[i]) ? Double.NaN : src[i] + offset;
            }
        } else {
            double factor = source.getToBaseFactor() / target.getToBaseFactor();
            for (int i = 0; i < src.length; i++) {
                result[i] = Double.isNaN(src[i]) ? Double.NaN : src[i] * factor;
            }
        }

        return new TimeSeriesSlice(slice.getStart(), slice.getEnd(),
                slice.getDimension(), result);
    }

    /**
     * Power → Energy: Wert × Stunden des Intervalls.
     * Konvertiert zuerst Power in kW (Base), dann × Stunden = kWh, dann in Target-Energy.
     */
    static TimeSeriesSlice convertPowerToEnergy(TimeSeriesSlice slice, Unit source, Unit target) {
        TimeDimension dim = slice.getDimension();
        double[] src = slice.getValues();
        double[] result = new double[src.length];

        double powerToBaseKw = source.getToBaseFactor();
        double baseKwhToTarget = 1.0 / target.getToBaseFactor();

        switch (dim) {
            case QUARTER_HOUR:
                applyFixedHours(src, result, 0.25, powerToBaseKw, baseKwhToTarget);
                break;
            case HOUR:
                applyFixedHours(src, result, 1.0, powerToBaseKw, baseKwhToTarget);
                break;
            case DAY:
                applyVariableHoursDay(src, result, slice.getStart().toLocalDate(),
                        powerToBaseKw, baseKwhToTarget);
                break;
            case MONTH:
                applyVariableHoursMonth(src, result, YearMonth.from(slice.getStart().toLocalDate()),
                        powerToBaseKw, baseKwhToTarget);
                break;
            case YEAR:
                applyVariableHoursYear(src, result, slice.getStart().getYear(),
                        powerToBaseKw, baseKwhToTarget);
                break;
            default:
                throw new IllegalArgumentException("Power→Energy nicht unterstützt für: " + dim);
        }

        return new TimeSeriesSlice(slice.getStart(), slice.getEnd(), dim, result);
    }

    /**
     * Energy → Power: Wert ÷ Stunden des Intervalls.
     * Konvertiert zuerst Energy in kWh (Base), dann ÷ Stunden = kW, dann in Target-Power.
     */
    static TimeSeriesSlice convertEnergyToPower(TimeSeriesSlice slice, Unit source, Unit target) {
        TimeDimension dim = slice.getDimension();
        double[] src = slice.getValues();
        double[] result = new double[src.length];

        double energyToBaseKwh = source.getToBaseFactor();
        double baseKwToTarget = 1.0 / target.getToBaseFactor();

        switch (dim) {
            case QUARTER_HOUR:
                applyFixedHoursDivide(src, result, 0.25, energyToBaseKwh, baseKwToTarget);
                break;
            case HOUR:
                applyFixedHoursDivide(src, result, 1.0, energyToBaseKwh, baseKwToTarget);
                break;
            case DAY:
                applyVariableHoursDayDivide(src, result, slice.getStart().toLocalDate(),
                        energyToBaseKwh, baseKwToTarget);
                break;
            case MONTH:
                applyVariableHoursMonthDivide(src, result,
                        YearMonth.from(slice.getStart().toLocalDate()),
                        energyToBaseKwh, baseKwToTarget);
                break;
            case YEAR:
                applyVariableHoursYearDivide(src, result, slice.getStart().getYear(),
                        energyToBaseKwh, baseKwToTarget);
                break;
            default:
                throw new IllegalArgumentException("Energy→Power nicht unterstützt für: " + dim);
        }

        return new TimeSeriesSlice(slice.getStart(), slice.getEnd(), dim, result);
    }

    // ================================================================
    // Stundenberechnungen (DST-aware)
    // ================================================================

    static double hoursInDay(LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(TimeSeriesSlice.ZONE);
        ZonedDateTime end = date.plusDays(1).atStartOfDay(TimeSeriesSlice.ZONE);
        return Duration.between(start, end).getSeconds() / 3600.0;
    }

    static double hoursInMonth(YearMonth ym) {
        LocalDate first = ym.atDay(1);
        LocalDate firstOfNext = ym.plusMonths(1).atDay(1);
        ZonedDateTime start = first.atStartOfDay(TimeSeriesSlice.ZONE);
        ZonedDateTime end = firstOfNext.atStartOfDay(TimeSeriesSlice.ZONE);
        return Duration.between(start, end).getSeconds() / 3600.0;
    }

    static double hoursInYear(int year) {
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        LocalDate jan1Next = LocalDate.of(year + 1, 1, 1);
        ZonedDateTime start = jan1.atStartOfDay(TimeSeriesSlice.ZONE);
        ZonedDateTime end = jan1Next.atStartOfDay(TimeSeriesSlice.ZONE);
        return Duration.between(start, end).getSeconds() / 3600.0;
    }

    // ================================================================
    // Power → Energy Hilfsmethoden
    // ================================================================

    private static void applyFixedHours(double[] src, double[] result, double hours,
                                         double powerToBase, double baseToTarget) {
        double factor = powerToBase * hours * baseToTarget;
        for (int i = 0; i < src.length; i++) {
            result[i] = Double.isNaN(src[i]) ? Double.NaN : src[i] * factor;
        }
    }

    private static void applyVariableHoursDay(double[] src, double[] result, LocalDate startDate,
                                               double powerToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInDay(startDate.plusDays(i));
                result[i] = src[i] * powerToBase * hours * baseToTarget;
            }
        }
    }

    private static void applyVariableHoursMonth(double[] src, double[] result, YearMonth startMonth,
                                                  double powerToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInMonth(startMonth.plusMonths(i));
                result[i] = src[i] * powerToBase * hours * baseToTarget;
            }
        }
    }

    private static void applyVariableHoursYear(double[] src, double[] result, int startYear,
                                                double powerToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInYear(startYear + i);
                result[i] = src[i] * powerToBase * hours * baseToTarget;
            }
        }
    }

    // ================================================================
    // Energy → Power Hilfsmethoden
    // ================================================================

    private static void applyFixedHoursDivide(double[] src, double[] result, double hours,
                                               double energyToBase, double baseToTarget) {
        double factor = energyToBase / hours * baseToTarget;
        for (int i = 0; i < src.length; i++) {
            result[i] = Double.isNaN(src[i]) ? Double.NaN : src[i] * factor;
        }
    }

    private static void applyVariableHoursDayDivide(double[] src, double[] result,
                                                      LocalDate startDate,
                                                      double energyToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInDay(startDate.plusDays(i));
                result[i] = src[i] * energyToBase / hours * baseToTarget;
            }
        }
    }

    private static void applyVariableHoursMonthDivide(double[] src, double[] result,
                                                        YearMonth startMonth,
                                                        double energyToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInMonth(startMonth.plusMonths(i));
                result[i] = src[i] * energyToBase / hours * baseToTarget;
            }
        }
    }

    private static void applyVariableHoursYearDivide(double[] src, double[] result, int startYear,
                                                       double energyToBase, double baseToTarget) {
        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                result[i] = Double.NaN;
            } else {
                double hours = hoursInYear(startYear + i);
                result[i] = src[i] * energyToBase / hours * baseToTarget;
            }
        }
    }

    // ================================================================
    // Validierung & Offset
    // ================================================================

    private static void validateSameCategory(Unit source, Unit target) {
        if (source.getCategory() != target.getCategory()) {
            throw new IllegalArgumentException(
                    "Konvertierung von " + source.getSymbol() + " (" + source.getCategory()
                    + ") nach " + target.getSymbol() + " (" + target.getCategory()
                    + ") nicht möglich");
        }
    }

    private static double getOffset(Unit source, Unit target) {
        // °C → K
        if (source == Unit.CELSIUS && target == Unit.KELVIN) return CELSIUS_TO_KELVIN_OFFSET;
        // K → °C
        if (source == Unit.KELVIN && target == Unit.CELSIUS) return -CELSIUS_TO_KELVIN_OFFSET;
        return 0.0;
    }
}
