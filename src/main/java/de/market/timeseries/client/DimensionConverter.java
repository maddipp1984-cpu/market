package de.market.timeseries.client;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Konvertiert TimeSeriesSlice zwischen Dimensionen.
 * Reine Mathematik, keine DB-Abhängigkeit, vollständig unit-testbar.
 */
class DimensionConverter {

    // ================================================================
    // Aggregation (hoch: fein → grob)
    // ================================================================

    static TimeSeriesSlice aggregate(TimeSeriesSlice source, TimeDimension target,
                                     AggregationFunction func) {
        TimeDimension src = source.getDimension();

        // Direkter Pfad: QH → H
        if (src == TimeDimension.QUARTER_HOUR && target == TimeDimension.HOUR) {
            return aggregateQhToH(source, func);
        }

        // Subdaily → Tag
        if (src.useTimestamptz() && target == TimeDimension.DAY) {
            return aggregateSubdailyToDay(source, func);
        }

        // Tag → Monat
        if (src == TimeDimension.DAY && target == TimeDimension.MONTH) {
            return aggregateDayToMonth(source, func);
        }

        // Tag → Jahr
        if (src == TimeDimension.DAY && target == TimeDimension.YEAR) {
            return aggregateDayToYear(source, func);
        }

        // Monat → Jahr
        if (src == TimeDimension.MONTH && target == TimeDimension.YEAR) {
            return aggregateMonthToYear(source, func);
        }

        // Kaskadierung: QH/H → Monat (über Tag)
        if (src.useTimestamptz() && target == TimeDimension.MONTH) {
            if (func == AggregationFunction.AVG) {
                // Gewichteter Durchschnitt: SUM kaskadieren, dann durch Quell-Intervalle teilen
                TimeSeriesSlice daily = aggregateSubdailyToDay(source, AggregationFunction.SUM);
                TimeSeriesSlice monthly = aggregateDayToMonth(daily, AggregationFunction.SUM);
                return weightedAverage(monthly, source, target);
            }
            TimeSeriesSlice daily = aggregateSubdailyToDay(source, func);
            return aggregateDayToMonth(daily, func);
        }

        // Kaskadierung: QH/H → Jahr (über Tag → Monat)
        if (src.useTimestamptz() && target == TimeDimension.YEAR) {
            if (func == AggregationFunction.AVG) {
                TimeSeriesSlice daily = aggregateSubdailyToDay(source, AggregationFunction.SUM);
                TimeSeriesSlice yearly = aggregateDayToYear(daily, AggregationFunction.SUM);
                return weightedAverage(yearly, source, target);
            }
            TimeSeriesSlice daily = aggregateSubdailyToDay(source, func);
            return aggregateDayToYear(daily, func);
        }

        // Kaskadierung: H → H über QH nicht nötig (gleiche Dimension)
        // Kaskadierung: Monat → Jahr
        // (bereits oben abgedeckt)

        throw new IllegalArgumentException(
                "Aggregation von " + src + " nach " + target + " nicht unterstützt");
    }

    /**
     * QH → H: Je 4 Quellwerte gruppieren.
     * DST-korrekt: 92 QH = 23 H, 100 QH = 25 H.
     */
    private static TimeSeriesSlice aggregateQhToH(TimeSeriesSlice source,
                                                   AggregationFunction func) {
        double[] src = source.getValues();
        int resultLen = src.length / 4;
        double[] result = new double[resultLen];

        for (int i = 0; i < resultLen; i++) {
            result[i] = applyFunction(src, i * 4, i * 4 + 4, func);
        }

        return new TimeSeriesSlice(source.getStart(), source.getEnd(),
                TimeDimension.HOUR, result);
    }

    /**
     * QH/H → Tag: Tagesweise gruppieren, DST-aware.
     */
    private static TimeSeriesSlice aggregateSubdailyToDay(TimeSeriesSlice source,
                                                           AggregationFunction func) {
        TimeDimension srcDim = source.getDimension();
        LocalDate startDate = source.getStart().toLocalDate();
        LocalDate endDate = source.getEnd().toLocalDate();
        if (!source.getEnd().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            endDate = endDate.plusDays(1);
        }

        List<Double> dayValues = new ArrayList<>();
        int offset = 0;
        double[] src = source.getValues();

        for (LocalDate day = startDate; day.isBefore(endDate) && offset < src.length;
             day = day.plusDays(1)) {
            int slotsForDay = srcDim.intervalsPerDay(day);

            // Anschnitt erster Tag
            if (day.equals(startDate)) {
                slotsForDay = slotsForDay - slotOffsetForTime(day, source.getStart(), srcDim);
            }
            // Anschnitt letzter Tag
            LocalDate lastDay = source.getEnd().toLocalDate();
            if (day.equals(lastDay) && !source.getEnd().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                int endSlot = slotOffsetForTime(day, source.getEnd(), srcDim);
                if (day.equals(startDate)) {
                    int startSlot = slotOffsetForTime(day, source.getStart(), srcDim);
                    slotsForDay = endSlot - startSlot;
                } else {
                    slotsForDay = endSlot;
                }
            }

            int actualSlots = Math.min(slotsForDay, src.length - offset);
            if (actualSlots <= 0) break;

            dayValues.add(applyFunction(src, offset, offset + actualSlots, func));
            offset += actualSlots;
        }

        double[] result = toPrimitive(dayValues);
        LocalDateTime newStart = startDate.atStartOfDay();
        LocalDateTime newEnd = endDate.atStartOfDay();
        return new TimeSeriesSlice(newStart, newEnd, TimeDimension.DAY, result);
    }

    /**
     * Tag → Monat: Tage pro Monat gruppieren (28-31).
     */
    private static TimeSeriesSlice aggregateDayToMonth(TimeSeriesSlice source,
                                                        AggregationFunction func) {
        double[] src = source.getValues();
        LocalDate startDate = source.getStart().toLocalDate();

        List<Double> monthValues = new ArrayList<>();
        int offset = 0;
        LocalDate current = startDate;

        while (offset < src.length) {
            YearMonth ym = YearMonth.from(current);
            int daysInMonth = ym.lengthOfMonth();

            // Erster Monat: ggf. nicht am 1. begonnen
            int daysFromStart = daysInMonth - current.getDayOfMonth() + 1;
            int daysToUse = Math.min(daysFromStart, src.length - offset);

            monthValues.add(applyFunction(src, offset, offset + daysToUse, func));
            offset += daysToUse;
            current = current.plusDays(daysToUse);
        }

        double[] result = toPrimitive(monthValues);
        LocalDateTime newStart = startDate.withDayOfMonth(1).atStartOfDay();
        YearMonth lastMonth = YearMonth.from(startDate.plusDays(src.length - 1));
        LocalDateTime newEnd = lastMonth.plusMonths(1).atDay(1).atStartOfDay();
        return new TimeSeriesSlice(newStart, newEnd, TimeDimension.MONTH, result);
    }

    /**
     * Tag → Jahr: Alle Tage eines Jahres gruppieren.
     */
    private static TimeSeriesSlice aggregateDayToYear(TimeSeriesSlice source,
                                                       AggregationFunction func) {
        double[] src = source.getValues();
        LocalDate startDate = source.getStart().toLocalDate();

        List<Double> yearValues = new ArrayList<>();
        int offset = 0;
        LocalDate current = startDate;

        while (offset < src.length) {
            int daysInYear = current.isLeapYear() ? 366 : 365;
            int dayOfYear = current.getDayOfYear();
            int daysRemaining = daysInYear - dayOfYear + 1;
            int daysToUse = Math.min(daysRemaining, src.length - offset);

            yearValues.add(applyFunction(src, offset, offset + daysToUse, func));
            offset += daysToUse;
            current = current.plusDays(daysToUse);
        }

        double[] result = toPrimitive(yearValues);
        LocalDateTime newStart = LocalDate.of(startDate.getYear(), 1, 1).atStartOfDay();
        int lastYear = startDate.plusDays(src.length - 1).getYear();
        LocalDateTime newEnd = LocalDate.of(lastYear + 1, 1, 1).atStartOfDay();
        return new TimeSeriesSlice(newStart, newEnd, TimeDimension.YEAR, result);
    }

    /**
     * Monat → Jahr: 12 (oder weniger) Monate gruppieren.
     */
    private static TimeSeriesSlice aggregateMonthToYear(TimeSeriesSlice source,
                                                         AggregationFunction func) {
        double[] src = source.getValues();
        LocalDate startDate = source.getStart().toLocalDate();

        List<Double> yearValues = new ArrayList<>();
        int offset = 0;
        YearMonth current = YearMonth.from(startDate);

        while (offset < src.length) {
            int monthsRemaining = 12 - current.getMonthValue() + 1;
            int monthsToUse = Math.min(monthsRemaining, src.length - offset);

            yearValues.add(applyFunction(src, offset, offset + monthsToUse, func));
            offset += monthsToUse;
            current = current.plusMonths(monthsToUse);
        }

        double[] result = toPrimitive(yearValues);
        LocalDateTime newStart = LocalDate.of(startDate.getYear(), 1, 1).atStartOfDay();
        int lastYear = YearMonth.from(startDate).plusMonths(src.length - 1).getYear();
        LocalDateTime newEnd = LocalDate.of(lastYear + 1, 1, 1).atStartOfDay();
        return new TimeSeriesSlice(newStart, newEnd, TimeDimension.YEAR, result);
    }

    // ================================================================
    // Disaggregation (runter: grob → fein)
    // ================================================================

    static TimeSeriesSlice disaggregate(TimeSeriesSlice source, TimeDimension target,
                                        AggregationFunction func) {
        TimeDimension src = source.getDimension();

        // Tag → H oder QH
        if (src == TimeDimension.DAY && target.useTimestamptz()) {
            return disaggregateDayToSubdaily(source, target, func);
        }

        // Monat → Tag
        if (src == TimeDimension.MONTH && target == TimeDimension.DAY) {
            return disaggregateMonthToDay(source, func);
        }

        // H → QH
        if (src == TimeDimension.HOUR && target == TimeDimension.QUARTER_HOUR) {
            return disaggregateHToQh(source, func);
        }

        // Kaskadierung: Monat → H/QH (über Tag)
        if (src == TimeDimension.MONTH && target.useTimestamptz()) {
            TimeSeriesSlice daily = disaggregateMonthToDay(source, func);
            return disaggregateDayToSubdaily(daily, target, func);
        }

        // Kaskadierung: Jahr → Monat
        if (src == TimeDimension.YEAR && target == TimeDimension.MONTH) {
            return disaggregateYearToMonth(source, func);
        }

        // Kaskadierung: Jahr → Tag (über Monat)
        if (src == TimeDimension.YEAR && target == TimeDimension.DAY) {
            TimeSeriesSlice monthly = disaggregateYearToMonth(source, func);
            return disaggregateMonthToDay(monthly, func);
        }

        // Kaskadierung: Jahr → H/QH (über Monat → Tag)
        if (src == TimeDimension.YEAR && target.useTimestamptz()) {
            TimeSeriesSlice monthly = disaggregateYearToMonth(source, func);
            TimeSeriesSlice daily = disaggregateMonthToDay(monthly, func);
            return disaggregateDayToSubdaily(daily, target, func);
        }

        throw new IllegalArgumentException(
                "Disaggregation von " + src + " nach " + target + " nicht unterstützt");
    }

    /**
     * Tag → H oder QH: DST-aware, Anzahl Slots variiert pro Tag.
     */
    private static TimeSeriesSlice disaggregateDayToSubdaily(TimeSeriesSlice source,
                                                              TimeDimension target,
                                                              AggregationFunction func) {
        double[] src = source.getValues();
        LocalDate startDate = source.getStart().toLocalDate();

        List<double[]> chunks = new ArrayList<>();
        int totalLength = 0;

        for (int i = 0; i < src.length; i++) {
            LocalDate day = startDate.plusDays(i);
            int slots = target.intervalsPerDay(day);
            double[] daySlots = new double[slots];

            if (Double.isNaN(src[i])) {
                Arrays.fill(daySlots, Double.NaN);
            } else {
                double value = (func == AggregationFunction.SUM) ? src[i] / slots : src[i];
                Arrays.fill(daySlots, value);
            }

            chunks.add(daySlots);
            totalLength += slots;
        }

        double[] result = flatten(chunks, totalLength);
        LocalDateTime newEnd = startDate.plusDays(src.length).atStartOfDay();
        return new TimeSeriesSlice(source.getStart(), newEnd, target, result);
    }

    /**
     * H → QH: Je 1 Stundenwert auf 4 QH-Werte verteilen.
     */
    private static TimeSeriesSlice disaggregateHToQh(TimeSeriesSlice source,
                                                      AggregationFunction func) {
        double[] src = source.getValues();
        double[] result = new double[src.length * 4];

        for (int i = 0; i < src.length; i++) {
            double value;
            if (Double.isNaN(src[i])) {
                value = Double.NaN;
            } else {
                value = (func == AggregationFunction.SUM) ? src[i] / 4.0 : src[i];
            }
            Arrays.fill(result, i * 4, i * 4 + 4, value);
        }

        return new TimeSeriesSlice(source.getStart(), source.getEnd(),
                TimeDimension.QUARTER_HOUR, result);
    }

    /**
     * Monat → Tag: Tage pro Monat variabel (28-31).
     */
    private static TimeSeriesSlice disaggregateMonthToDay(TimeSeriesSlice source,
                                                           AggregationFunction func) {
        double[] src = source.getValues();
        LocalDate startDate = source.getStart().toLocalDate();

        List<Double> dayValues = new ArrayList<>();
        YearMonth current = YearMonth.from(startDate);

        for (int i = 0; i < src.length; i++) {
            int daysInMonth = current.lengthOfMonth();

            if (Double.isNaN(src[i])) {
                for (int d = 0; d < daysInMonth; d++) dayValues.add(Double.NaN);
            } else {
                double value = (func == AggregationFunction.SUM) ? src[i] / daysInMonth : src[i];
                for (int d = 0; d < daysInMonth; d++) dayValues.add(value);
            }

            current = current.plusMonths(1);
        }

        double[] result = toPrimitive(dayValues);
        LocalDateTime newEnd = current.atDay(1).atStartOfDay();
        return new TimeSeriesSlice(source.getStart(), newEnd, TimeDimension.DAY, result);
    }

    /**
     * Jahr → Monat: 12 Monate pro Jahr.
     */
    private static TimeSeriesSlice disaggregateYearToMonth(TimeSeriesSlice source,
                                                            AggregationFunction func) {
        double[] src = source.getValues();
        int startYear = source.getStart().getYear();

        List<Double> monthValues = new ArrayList<>();

        for (int i = 0; i < src.length; i++) {
            if (Double.isNaN(src[i])) {
                for (int m = 0; m < 12; m++) monthValues.add(Double.NaN);
            } else {
                double value = (func == AggregationFunction.SUM) ? src[i] / 12.0 : src[i];
                for (int m = 0; m < 12; m++) monthValues.add(value);
            }
        }

        double[] result = toPrimitive(monthValues);
        LocalDateTime newStart = LocalDate.of(startYear, 1, 1).atStartOfDay();
        LocalDateTime newEnd = LocalDate.of(startYear + src.length, 1, 1).atStartOfDay();
        return new TimeSeriesSlice(newStart, newEnd, TimeDimension.MONTH, result);
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    /**
     * Gewichteter Durchschnitt für kaskadierte Aggregation.
     * Teilt die SUMmen durch die tatsächliche Anzahl der Quell-Intervalle
     * pro Zielperiode (DST-korrekt).
     */
    private static TimeSeriesSlice weightedAverage(TimeSeriesSlice sumSlice,
                                                    TimeSeriesSlice source,
                                                    TimeDimension target) {
        double[] sums = sumSlice.getValues();
        double[] result = new double[sums.length];
        TimeDimension srcDim = source.getDimension();
        double[] srcValues = source.getValues();

        if (target == TimeDimension.MONTH) {
            LocalDate startDate = source.getStart().toLocalDate();
            int srcOffset = 0;

            for (int m = 0; m < sums.length; m++) {
                // Anzahl Quell-Intervalle in diesem Monat zählen (nur nicht-NaN)
                YearMonth ym = YearMonth.from(startDate);
                int daysInMonth = ym.lengthOfMonth();
                int intervalsInMonth = 0;

                for (int d = 0; d < daysInMonth && srcOffset + intervalsInMonth < srcValues.length; d++) {
                    LocalDate day = startDate.plusDays(d);
                    int slotsForDay = srcDim.intervalsPerDay(day);
                    for (int s = 0; s < slotsForDay && srcOffset + intervalsInMonth < srcValues.length; s++) {
                        if (!Double.isNaN(srcValues[srcOffset + intervalsInMonth])) {
                            // zählt mit
                        }
                        intervalsInMonth++;
                    }
                }

                // Nicht-NaN-Werte im Quellbereich zählen
                int nonNanCount = 0;
                for (int i = srcOffset; i < srcOffset + intervalsInMonth && i < srcValues.length; i++) {
                    if (!Double.isNaN(srcValues[i])) {
                        nonNanCount++;
                    }
                }

                result[m] = nonNanCount > 0 ? sums[m] / nonNanCount : Double.NaN;
                srcOffset += intervalsInMonth;
                startDate = startDate.plusDays(daysInMonth);
            }
        } else if (target == TimeDimension.YEAR) {
            LocalDate startDate = source.getStart().toLocalDate();
            int srcOffset = 0;

            for (int y = 0; y < sums.length; y++) {
                int year = startDate.getYear();
                int daysInYear = startDate.isLeapYear() ? 366 : 365;
                int dayOfYear = startDate.getDayOfYear();
                int daysRemaining = daysInYear - dayOfYear + 1;

                // Alle Quell-Intervalle in diesem Jahr zählen
                int intervalsInYear = 0;
                for (int d = 0; d < daysRemaining; d++) {
                    LocalDate day = startDate.plusDays(d);
                    int slotsForDay = srcDim.intervalsPerDay(day);
                    intervalsInYear += slotsForDay;
                    if (srcOffset + intervalsInYear >= srcValues.length) {
                        intervalsInYear = srcValues.length - srcOffset;
                        break;
                    }
                }

                int nonNanCount = 0;
                for (int i = srcOffset; i < srcOffset + intervalsInYear && i < srcValues.length; i++) {
                    if (!Double.isNaN(srcValues[i])) {
                        nonNanCount++;
                    }
                }

                result[y] = nonNanCount > 0 ? sums[y] / nonNanCount : Double.NaN;
                srcOffset += intervalsInYear;
                startDate = LocalDate.of(year + 1, 1, 1);
            }
        }

        return new TimeSeriesSlice(sumSlice.getStart(), sumSlice.getEnd(),
                target, result);
    }

    /**
     * Wendet die Aggregationsfunktion auf values[from..to) an.
     * NaN-Werte werden ignoriert. Alle NaN → Ergebnis = NaN.
     */
    static double applyFunction(double[] values, int from, int to,
                                AggregationFunction func) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int count = 0;

        for (int i = from; i < to; i++) {
            if (!Double.isNaN(values[i])) {
                sum += values[i];
                min = Math.min(min, values[i]);
                max = Math.max(max, values[i]);
                count++;
            }
        }

        if (count == 0) return Double.NaN;

        switch (func) {
            case SUM: return sum;
            case AVG: return sum / count;
            case MIN: return min;
            case MAX: return max;
            default: throw new IllegalArgumentException("Unbekannte Funktion: " + func);
        }
    }

    private static int slotOffsetForTime(LocalDate date, LocalDateTime dateTime,
                                          TimeDimension dim) {
        java.time.ZonedDateTime startOfDay = date.atStartOfDay(TimeSeriesSlice.ZONE);
        java.time.ZonedDateTime target = date.atTime(dateTime.toLocalTime())
                .atZone(TimeSeriesSlice.ZONE);
        long seconds = java.time.Duration.between(startOfDay, target).getSeconds();
        long intervalSeconds = dim == TimeDimension.QUARTER_HOUR ? 900 : 3600;
        return (int) (seconds / intervalSeconds);
    }

    private static double[] toPrimitive(List<Double> list) {
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static double[] flatten(List<double[]> chunks, int totalLength) {
        double[] result = new double[totalLength];
        int offset = 0;
        for (double[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }
}
