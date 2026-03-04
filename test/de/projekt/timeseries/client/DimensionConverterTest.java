package de.projekt.timeseries.client;

import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesSlice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DimensionConverterTest {

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private static LocalDateTime dt(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0);
    }

    private static LocalDateTime dt(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private static double[] filled(int length, double value) {
        double[] arr = new double[length];
        Arrays.fill(arr, value);
        return arr;
    }

    private static double[] sequence(int length) {
        double[] arr = new double[length];
        for (int i = 0; i < length; i++) arr[i] = i;
        return arr;
    }

    private static boolean allNaN(double[] arr) {
        for (double v : arr) {
            if (!Double.isNaN(v)) return false;
        }
        return true;
    }

    private static TimeSeriesSlice slice(TimeDimension dim, LocalDateTime start,
                                          LocalDateTime end, double[] values) {
        return new TimeSeriesSlice(start, end, dim, values);
    }

    // ================================================================
    // applyFunction
    // ================================================================

    @Nested
    class ApplyFunctionTest {

        @Test
        void sum() {
            double[] v = {1.0, 2.0, 3.0, 4.0};
            assertEquals(10.0, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.SUM));
        }

        @Test
        void avg() {
            double[] v = {1.0, 2.0, 3.0, 4.0};
            assertEquals(2.5, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.AVG));
        }

        @Test
        void min() {
            double[] v = {3.0, 1.0, 4.0, 2.0};
            assertEquals(1.0, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.MIN));
        }

        @Test
        void max() {
            double[] v = {3.0, 1.0, 4.0, 2.0};
            assertEquals(4.0, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.MAX));
        }

        @Test
        void nanSkipped() {
            double[] v = {1.0, Double.NaN, 3.0, Double.NaN};
            assertEquals(4.0, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.SUM));
            assertEquals(2.0, DimensionConverter.applyFunction(v, 0, 4, AggregationFunction.AVG));
        }

        @Test
        void allNaN_returnsNaN() {
            double[] v = {Double.NaN, Double.NaN, Double.NaN};
            assertTrue(Double.isNaN(DimensionConverter.applyFunction(v, 0, 3, AggregationFunction.SUM)));
            assertTrue(Double.isNaN(DimensionConverter.applyFunction(v, 0, 3, AggregationFunction.AVG)));
        }

        @Test
        void subrange() {
            double[] v = {10.0, 20.0, 30.0, 40.0, 50.0};
            assertEquals(90.0, DimensionConverter.applyFunction(v, 1, 4, AggregationFunction.SUM));
        }
    }

    // ================================================================
    // Aggregation: QH → H
    // ================================================================

    @Nested
    class QhToHTest {

        @Test
        void normalDay_sum() {
            // 96 QH → 24 H
            double[] qh = filled(96, 10.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(24, result.size());
            assertEquals(TimeDimension.HOUR, result.getDimension());
            assertEquals(40.0, result.getValue(0)); // 4 × 10
        }

        @Test
        void normalDay_avg() {
            double[] qh = new double[96];
            for (int i = 0; i < 96; i++) qh[i] = i;
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.AVG);

            assertEquals(24, result.size());
            // Erste Stunde: avg(0,1,2,3) = 1.5
            assertEquals(1.5, result.getValue(0));
        }

        @Test
        void dstSpring_92QH_to_23H() {
            // 30. März 2025: Sommerzeit, 92 QH
            double[] qh = filled(92, 5.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 3, 30), dt(2025, 3, 31), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(23, result.size());
            assertEquals(20.0, result.getValue(0)); // 4 × 5
        }

        @Test
        void dstFall_100QH_to_25H() {
            // 26. Oktober 2025: Winterzeit, 100 QH
            double[] qh = filled(100, 2.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 10, 26), dt(2025, 10, 27), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(25, result.size());
            assertEquals(8.0, result.getValue(0)); // 4 × 2
        }

        @Test
        void nanInOneSlot_skipped() {
            double[] qh = {1.0, Double.NaN, 3.0, 4.0};
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15), dt(2025, 6, 15, 1, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(8.0, result.getValue(0)); // 1 + 3 + 4
        }

        @Test
        void allNaN_staysNaN() {
            double[] qh = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15), dt(2025, 6, 15, 1, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertTrue(Double.isNaN(result.getValue(0)));
        }
    }

    // ================================================================
    // Aggregation: Subdaily → Tag
    // ================================================================

    @Nested
    class SubdailyToDayTest {

        @Test
        void qh_oneDay_sum() {
            double[] qh = filled(96, 10.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(TimeDimension.DAY, result.getDimension());
            assertEquals(960.0, result.getValue(0)); // 96 × 10
        }

        @Test
        void qh_threeDays_sum() {
            double[] qh = new double[288]; // 3 × 96
            Arrays.fill(qh, 0, 96, 1.0);
            Arrays.fill(qh, 96, 192, 2.0);
            Arrays.fill(qh, 192, 288, 3.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 18), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(3, result.size());
            assertEquals(96.0, result.getValue(0));  // 96 × 1
            assertEquals(192.0, result.getValue(1)); // 96 × 2
            assertEquals(288.0, result.getValue(2)); // 96 × 3
        }

        @Test
        void h_oneDay_avg() {
            double[] h = new double[24];
            for (int i = 0; i < 24; i++) h[i] = i;
            TimeSeriesSlice src = slice(TimeDimension.HOUR, dt(2025, 6, 15), dt(2025, 6, 16), h);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.AVG);

            assertEquals(1, result.size());
            assertEquals(11.5, result.getValue(0)); // avg(0..23)
        }

        @Test
        void dstSpring_day_correctSum() {
            // 30. März: 92 QH
            double[] qh = filled(92, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 3, 30), dt(2025, 3, 31), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(92.0, result.getValue(0));
        }

        @Test
        void dstFall_day_correctSum() {
            // 26. Oktober: 100 QH
            double[] qh = filled(100, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 10, 26), dt(2025, 10, 27), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(100.0, result.getValue(0));
        }

        @Test
        void rangeAcrossDst_correctDayCount() {
            // 29. März (96) + 30. März (92) + 31. März (96) = 284 QH → 3 Tage
            double[] qh = filled(284, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 3, 29), dt(2025, 4, 1), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(3, result.size());
            assertEquals(96.0, result.getValue(0));
            assertEquals(92.0, result.getValue(1));
            assertEquals(96.0, result.getValue(2));
        }
    }

    // ================================================================
    // Aggregation: Tag → Monat
    // ================================================================

    @Nested
    class DayToMonthTest {

        @Test
        void january_31Days_sum() {
            double[] days = filled(31, 10.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2025, 2, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(310.0, result.getValue(0));
        }

        @Test
        void february_28Days_leapYear_29Days() {
            // 2024 = Schaltjahr
            double[] days = filled(29, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2024, 2, 1), dt(2024, 3, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(29.0, result.getValue(0));
        }

        @Test
        void threeMonths_sum() {
            // Jan (31) + Feb (28) + März (31) = 90 Tage
            double[] days = filled(90, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2025, 4, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(3, result.size());
            assertEquals(31.0, result.getValue(0)); // Januar
            assertEquals(28.0, result.getValue(1)); // Februar
            assertEquals(31.0, result.getValue(2)); // März
        }
    }

    // ================================================================
    // Aggregation: Tag → Jahr
    // ================================================================

    @Nested
    class DayToYearTest {

        @Test
        void fullYear_sum() {
            double[] days = filled(365, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2026, 1, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(365.0, result.getValue(0));
        }

        @Test
        void leapYear_sum() {
            double[] days = filled(366, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2024, 1, 1), dt(2025, 1, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(366.0, result.getValue(0));
        }
    }

    // ================================================================
    // Aggregation: Monat → Jahr
    // ================================================================

    @Nested
    class MonthToYearTest {

        @Test
        void fullYear_sum() {
            double[] months = filled(12, 100.0);
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2026, 1, 1), months);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(1200.0, result.getValue(0));
        }

        @Test
        void twoYears_avg() {
            double[] months = new double[24];
            Arrays.fill(months, 0, 12, 10.0);
            Arrays.fill(months, 12, 24, 20.0);
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2027, 1, 1), months);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.AVG);

            assertEquals(2, result.size());
            assertEquals(10.0, result.getValue(0));
            assertEquals(20.0, result.getValue(1));
        }
    }

    // ================================================================
    // Kaskadierung
    // ================================================================

    @Nested
    class CascadeTest {

        @Test
        void qhToMonth_cascadesViaDay() {
            // 31 Tage Januar × 96 QH = 2976
            double[] qh = filled(2976, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 1, 1), dt(2025, 2, 1), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(TimeDimension.MONTH, result.getDimension());
            assertEquals(2976.0, result.getValue(0));
        }

        @Test
        void qhToYear_cascadesViaDayToYear() {
            // Vereinfacht: 3 Tage × 96 = 288 QH, als "Jahr" ab 1. Januar
            // (nicht volles Jahr, aber kaskadiert trotzdem korrekt)
            double[] qh = filled(288, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 1, 1), dt(2025, 1, 4), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals(288.0, result.getValue(0));
        }
    }

    // ================================================================
    // Disaggregation: Tag → QH/H
    // ================================================================

    @Nested
    class DayToSubdailyTest {

        @Test
        void dayToQh_sum_distributesEvenly() {
            double[] days = {960.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(96, result.size());
            assertEquals(TimeDimension.QUARTER_HOUR, result.getDimension());
            assertEquals(10.0, result.getValue(0)); // 960 / 96
            assertEquals(10.0, result.getValue(95));
        }

        @Test
        void dayToH_sum_distributesEvenly() {
            double[] days = {240.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(24, result.size());
            assertEquals(10.0, result.getValue(0)); // 240 / 24
        }

        @Test
        void dayToQh_avg_repeats() {
            double[] days = {42.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.AVG);

            assertEquals(96, result.size());
            assertEquals(42.0, result.getValue(0)); // Wiederholung
            assertEquals(42.0, result.getValue(95));
        }

        @Test
        void dstSpring_dayToQh_92Slots() {
            double[] days = {92.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 3, 30), dt(2025, 3, 31), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(92, result.size());
            assertEquals(1.0, result.getValue(0)); // 92 / 92
        }

        @Test
        void dstFall_dayToQh_100Slots() {
            double[] days = {100.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 10, 26), dt(2025, 10, 27), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(100, result.size());
            assertEquals(1.0, result.getValue(0)); // 100 / 100
        }

        @Test
        void nanDay_allQhNaN() {
            double[] days = {Double.NaN};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(96, result.size());
            assertTrue(allNaN(result.getValues()));
        }

        @Test
        void twoDays_differentValues() {
            double[] days = {96.0, 192.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 17), days);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(192, result.size());
            assertEquals(1.0, result.getValue(0));   // 96 / 96
            assertEquals(2.0, result.getValue(96));  // 192 / 96
        }
    }

    // ================================================================
    // Disaggregation: H → QH
    // ================================================================

    @Nested
    class HToQhTest {

        @Test
        void sum_distributesBy4() {
            double[] h = {40.0, 80.0};
            TimeSeriesSlice src = slice(TimeDimension.HOUR, dt(2025, 6, 15), dt(2025, 6, 15, 2, 0), h);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(8, result.size());
            assertEquals(10.0, result.getValue(0)); // 40 / 4
            assertEquals(20.0, result.getValue(4)); // 80 / 4
        }

        @Test
        void avg_repeats() {
            double[] h = {42.0};
            TimeSeriesSlice src = slice(TimeDimension.HOUR, dt(2025, 6, 15), dt(2025, 6, 15, 1, 0), h);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.AVG);

            assertEquals(4, result.size());
            assertEquals(42.0, result.getValue(0));
            assertEquals(42.0, result.getValue(3));
        }
    }

    // ================================================================
    // Disaggregation: Monat → Tag
    // ================================================================

    @Nested
    class MonthToDayTest {

        @Test
        void january_sum() {
            double[] months = {310.0};
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2025, 2, 1), months);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(31, result.size());
            assertEquals(10.0, result.getValue(0)); // 310 / 31
        }

        @Test
        void february_leapYear_sum() {
            double[] months = {29.0};
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2024, 2, 1), dt(2024, 3, 1), months);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(29, result.size());
            assertEquals(1.0, result.getValue(0)); // 29 / 29
        }

        @Test
        void twoMonths_differentLengths() {
            double[] months = {31.0, 28.0}; // Jan + Feb 2025
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2025, 3, 1), months);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(59, result.size()); // 31 + 28
            assertEquals(1.0, result.getValue(0));  // Jan: 31/31
            assertEquals(1.0, result.getValue(31)); // Feb: 28/28
        }

        @Test
        void nanMonth_allDaysNaN() {
            double[] months = {Double.NaN};
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2025, 2, 1), months);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(31, result.size());
            assertTrue(allNaN(result.getValues()));
        }
    }

    // ================================================================
    // Disaggregation: Jahr → Monat
    // ================================================================

    @Nested
    class YearToMonthTest {

        @Test
        void sum_distributesBy12() {
            double[] years = {1200.0};
            TimeSeriesSlice src = slice(TimeDimension.YEAR, dt(2025, 1, 1), dt(2026, 1, 1), years);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(12, result.size());
            assertEquals(100.0, result.getValue(0)); // 1200 / 12
        }

        @Test
        void avg_repeats() {
            double[] years = {42.0};
            TimeSeriesSlice src = slice(TimeDimension.YEAR, dt(2025, 1, 1), dt(2026, 1, 1), years);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.MONTH, AggregationFunction.AVG);

            assertEquals(12, result.size());
            assertEquals(42.0, result.getValue(0));
            assertEquals(42.0, result.getValue(11));
        }
    }

    // ================================================================
    // Disaggregation: Kaskadierung
    // ================================================================

    @Nested
    class DisaggregateCascadeTest {

        @Test
        void monthToQh_cascadesViaDayToQh() {
            // 1 Monat (Jan, 31 Tage) → QH: 31 × 96 = 2976
            double[] months = {2976.0};
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2025, 2, 1), months);

            TimeSeriesSlice result = DimensionConverter.disaggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM);

            assertEquals(2976, result.size());
            assertEquals(TimeDimension.QUARTER_HOUR, result.getDimension());
            // 2976 / 31 Tage = 96 pro Tag, dann 96 / 96 QH = 1.0 pro QH
            assertEquals(1.0, result.getValue(0), 0.001);
        }
    }
}
