package de.projekt.timeseries.client;

import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TimeSeriesSlice;
import de.projekt.timeseries.model.Unit;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Öffentliche Entwickler-API zum Lesen und Schreiben von Zeitreihen.
 * Unterstützt automatische Dimensionskonvertierung (Aggregation/Disaggregation)
 * und Unit-Konvertierung (Faktor, Offset, Power↔Energy).
 */
@Component
public class TimeSeriesClient {

    private static final AggregationFunction DEFAULT_FUNCTION = AggregationFunction.SUM;

    private final TimeSeriesService service;

    public TimeSeriesClient(TimeSeriesService service) {
        this.service = service;
    }

    /**
     * Liest eine Zeitreihe in ihrer nativen Dimension.
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        return service.read(tsId, start, end);
    }

    /**
     * Liest eine Zeitreihe und konvertiert in die Zieldimension (Default: SUM).
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                TimeDimension targetDimension) throws SQLException {
        return read(tsId, start, end, targetDimension, DEFAULT_FUNCTION);
    }

    /**
     * Liest eine Zeitreihe und konvertiert nur die Unit (keine Dimensionsänderung).
     *
     * @param tsId       ID der Zeitreihe
     * @param start      Beginn (inklusiv)
     * @param end        Ende (exklusiv)
     * @param targetUnit Ziel-Unit
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                Unit targetUnit) throws SQLException {
        return read(tsId, start, end, null, DEFAULT_FUNCTION, targetUnit);
    }

    /**
     * Liest eine Zeitreihe und konvertiert in die Zieldimension mit der angegebenen Funktion.
     *
     * @param tsId            ID der Zeitreihe
     * @param start           Beginn (inklusiv)
     * @param end             Ende (exklusiv)
     * @param targetDimension Zieldimension (null = native Dimension)
     * @param function        Aggregationsfunktion
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                TimeDimension targetDimension,
                                AggregationFunction function) throws SQLException {
        return read(tsId, start, end, targetDimension, function, null);
    }

    /**
     * Liest eine Zeitreihe mit optionaler Dimensions- und Unit-Konvertierung.
     *
     * <p>Reihenfolge bei Power→Energy + Aggregation: Unit ZUERST (QH-MW × 0.25h = QH-MWh, dann SUM).
     * Bei Energy→Power + Aggregation: Dimension ZUERST (SUM(MWh), dann ÷ Stunden).
     * Bei reiner Faktor-/Offset-Konvertierung: Unit zuerst (konsistent).</p>
     *
     * @param tsId            ID der Zeitreihe
     * @param start           Beginn (inklusiv)
     * @param end             Ende (exklusiv)
     * @param targetDimension Zieldimension (null = native Dimension)
     * @param function        Aggregationsfunktion
     * @param targetUnit      Ziel-Unit (null = keine Unit-Konvertierung)
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                TimeDimension targetDimension,
                                AggregationFunction function,
                                Unit targetUnit) throws SQLException {
        TimeSeriesHeader header = service.getHeader(tsId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Zeitreihe nicht gefunden: tsId=" + tsId));

        TimeDimension nativeDim = header.getTimeDimension();
        TimeDimension targetDim = targetDimension != null ? targetDimension : nativeDim;
        AggregationFunction func = function != null ? function : DEFAULT_FUNCTION;
        Unit sourceUnit = header.getUnit();
        boolean needsDimConvert = targetDim != nativeDim;
        boolean needsUnitConvert = targetUnit != null && targetUnit != sourceUnit;

        // Rohdaten lesen
        TimeSeriesSlice slice = service.read(tsId, start, end);

        if (!needsDimConvert && !needsUnitConvert) {
            return slice;
        }

        // Nur Unit-Konvertierung
        if (!needsDimConvert) {
            return applyUnitConversion(slice, sourceUnit, targetUnit);
        }

        // Nur Dimensions-Konvertierung
        if (!needsUnitConvert) {
            return applyDimensionConversion(slice, nativeDim, targetDim, func);
        }

        // Beides: Reihenfolge hängt vom Konvertierungstyp ab
        if (sourceUnit.getCategory() == Unit.UnitCategory.ACTIVE_POWER
                && targetUnit.getCategory() == Unit.UnitCategory.ENERGY) {
            // Power→Energy ZUERST, dann Aggregation
            slice = applyUnitConversion(slice, sourceUnit, targetUnit);
            return applyDimensionConversion(slice, nativeDim, targetDim, func);
        } else if (sourceUnit.getCategory() == Unit.UnitCategory.ENERGY
                && targetUnit.getCategory() == Unit.UnitCategory.ACTIVE_POWER) {
            // Dimension ZUERST, dann Energy→Power
            slice = applyDimensionConversion(slice, nativeDim, targetDim, func);
            return applyUnitConversion(slice, sourceUnit, targetUnit);
        } else {
            // Faktor/Offset: Unit zuerst (konsistent)
            slice = applyUnitConversion(slice, sourceUnit, targetUnit);
            return applyDimensionConversion(slice, nativeDim, targetDim, func);
        }
    }

    // ================================================================
    // Schreiben
    // ================================================================

    /**
     * Schreibt ein TimeSeriesSlice. Die Dimensionskonvertierung erfolgt automatisch:
     * Stimmt die Dimension des Slices nicht mit der DB-Dimension überein, wird
     * intern aggregiert oder disaggregiert (Gleichverteilung, DST-aware).
     *
     * <p>Der Entwickler muss nicht wissen, in welcher Dimension die Zeitreihe
     * in der Datenbank vorliegt — das System entscheidet selbstständig.</p>
     */
    public void write(long tsId, TimeSeriesSlice slice) throws SQLException {
        TimeSeriesHeader header = service.getHeader(tsId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Zeitreihe nicht gefunden: tsId=" + tsId));

        TimeDimension dbDim = header.getTimeDimension();
        TimeDimension sourceDim = slice.getDimension();

        TimeSeriesSlice nativeSlice;
        if (sourceDim == dbDim) {
            nativeSlice = slice;
        } else if (sourceDim.canAggregateTo(dbDim)) {
            // Source feiner als DB → aggregieren (z.B. QH → Tag)
            nativeSlice = DimensionConverter.aggregate(slice, dbDim, AggregationFunction.SUM);
        } else if (sourceDim.canDisaggregateTo(dbDim)) {
            // Source gröber als DB → disaggregieren (z.B. Tag → QH, Gleichverteilung)
            nativeSlice = DimensionConverter.disaggregate(slice, dbDim, AggregationFunction.SUM);
        } else {
            throw new IllegalArgumentException(
                    "Konvertierung von " + sourceDim + " nach " + dbDim + " nicht möglich");
        }

        writeSlice(tsId, nativeSlice);
    }

    /**
     * Schreibt ein Slice in der nativen DB-Dimension.
     */
    private void writeSlice(long tsId, TimeSeriesSlice slice) throws SQLException {
        if (slice.getDimension().useTimestamptz()) {
            writeSubdaily(tsId, slice);
        } else {
            writeSimpleSlice(tsId, slice);
        }
    }

    /**
     * Schreibt subdaily-Daten (QH/H) tageweise über writeDay.
     * DST-aware: Anzahl Werte pro Tag variiert (92/96/100 für QH, 23/24/25 für H).
     */
    private void writeSubdaily(long tsId, TimeSeriesSlice slice) throws SQLException {
        double[] values = slice.getValues();
        LocalDate startDate = slice.getStart().toLocalDate();
        TimeDimension dim = slice.getDimension();

        int offset = 0;
        LocalDate day = startDate;

        while (offset < values.length) {
            int slotsForDay = dim.intervalsPerDay(day);
            int remaining = values.length - offset;
            int count = Math.min(slotsForDay, remaining);

            double[] dayValues = new double[count];
            System.arraycopy(values, offset, dayValues, 0, count);

            service.writeDay(tsId, day, dayValues);

            offset += count;
            day = day.plusDays(1);
        }
    }

    /**
     * Schreibt DAY/MONTH/YEAR-Daten einzeln über writeSimple.
     */
    private void writeSimpleSlice(long tsId, TimeSeriesSlice slice) throws SQLException {
        double[] values = slice.getValues();
        TimeDimension dim = slice.getDimension();
        LocalDate startDate = slice.getStart().toLocalDate();

        for (int i = 0; i < values.length; i++) {
            if (dim == TimeDimension.YEAR) {
                service.writeSimple(tsId, startDate.getYear() + i, values[i]);
            } else if (dim == TimeDimension.MONTH) {
                LocalDate date = startDate.plusMonths(i);
                service.writeSimple(tsId, date, values[i]);
            } else {
                // DAY
                LocalDate date = startDate.plusDays(i);
                service.writeSimple(tsId, date, values[i]);
            }
        }
    }

    private TimeSeriesSlice applyUnitConversion(TimeSeriesSlice slice, Unit source, Unit target) {
        if (source.isConvertibleTo(target)) {
            return UnitConverter.convert(slice, source, target);
        } else if (source.isCrossDomainConvertibleTo(target)) {
            if (source.getCategory() == Unit.UnitCategory.ACTIVE_POWER) {
                return UnitConverter.convertPowerToEnergy(slice, source, target);
            } else {
                return UnitConverter.convertEnergyToPower(slice, source, target);
            }
        } else {
            throw new IllegalArgumentException(
                    "Unit-Konvertierung von " + source.getSymbol()
                    + " nach " + target.getSymbol() + " nicht möglich");
        }
    }

    private TimeSeriesSlice applyDimensionConversion(TimeSeriesSlice slice,
                                                      TimeDimension nativeDim,
                                                      TimeDimension targetDim,
                                                      AggregationFunction func) {
        if (nativeDim.canAggregateTo(targetDim)) {
            return DimensionConverter.aggregate(slice, targetDim, func);
        } else if (nativeDim.canDisaggregateTo(targetDim)) {
            return DimensionConverter.disaggregate(slice, targetDim, func);
        } else {
            throw new IllegalArgumentException(
                    "Konvertierung von " + nativeDim + " nach " + targetDim + " nicht möglich");
        }
    }
}
