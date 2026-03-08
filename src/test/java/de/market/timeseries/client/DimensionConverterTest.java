package de.market.timeseries.client;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
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

        @Test
        void qhToMonth_avg_dstWeighted() {
            // März 2025: 30 normale Tage (96 QH) + 1 DST-Tag am 30. März (92 QH)
            // 30 × 96 + 92 = 2972 QH
            // Alle QH-Werte = 10.0
            // Korrekter gewichteter AVG = (2972 × 10.0) / 2972 = 10.0
            // Naiver AVG wäre: (30 × 10.0 + 10.0) / 31 = 10.0 (bei gleichen Werten kein Unterschied)

            // Besser: unterschiedliche Werte pro Tag, damit der Unterschied sichtbar wird
            // 29 normale Tage à 96 QH = 2784, DST-Tag (30. März) 92 QH, 31. März 96 QH = 2972
            // Tag 1-29: QH-Werte = 1.0 → Tagessumme je 96.0
            // Tag 30 (DST): QH-Werte = 2.0 → Tagessumme 184.0
            // Tag 31: QH-Werte = 1.0 → Tagessumme 96.0
            int normalDays = 29 * 96;   // 2784
            int dstDay = 92;             // 30. März
            int lastDay = 96;            // 31. März
            double[] qh = new double[normalDays + dstDay + lastDay]; // 2972

            Arrays.fill(qh, 0, normalDays, 1.0);
            Arrays.fill(qh, normalDays, normalDays + dstDay, 2.0);
            Arrays.fill(qh, normalDays + dstDay, qh.length, 1.0);

            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 3, 1), dt(2025, 4, 1), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.AVG);

            assertEquals(1, result.size());
            // Gewichteter AVG = (2784 × 1.0 + 92 × 2.0 + 96 × 1.0) / 2972
            //                  = (2784 + 184 + 96) / 2972
            //                  = 3064 / 2972 ≈ 1.030957
            double expected = 3064.0 / 2972.0;
            assertEquals(expected, result.getValue(0), 1e-6);

            // Naiver (falscher) AVG wäre:
            // Tag-AVGs: 29×1.0 + 1×2.0 + 1×1.0 = 32.0 → 32.0/31 ≈ 1.032258
            // Differenz: ~0.0013 — klein aber abrechnungsrelevant!
        }

        @Test
        void qhToYear_avg_dstWeighted() {
            // 3 Tage: 29. März (96 QH), 30. März DST (92 QH), 31. März (96 QH) = 284 QH
            // Tag 1: Werte = 1.0, Tag 2 (DST): Werte = 3.0, Tag 3: Werte = 1.0
            double[] qh = new double[284];
            Arrays.fill(qh, 0, 96, 1.0);
            Arrays.fill(qh, 96, 96 + 92, 3.0);
            Arrays.fill(qh, 96 + 92, 284, 1.0);

            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 3, 29), dt(2025, 4, 1), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.AVG);

            assertEquals(1, result.size());
            // Gewichteter AVG = (96×1.0 + 92×3.0 + 96×1.0) / 284
            //                  = (96 + 276 + 96) / 284 = 468 / 284 ≈ 1.647887
            double expected = 468.0 / 284.0;
            assertEquals(expected, result.getValue(0), 1e-6);
        }
    }

    // ================================================================
    // Aggregation: Metadaten-Prüfung
    // ================================================================

    @Nested
    class MetadataTest {

        @Test
        void qhToH_metadataCorrect() {
            double[] qh = filled(96, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.SUM);

            assertEquals(dt(2025, 6, 15), result.getStart());
            assertEquals(dt(2025, 6, 16), result.getEnd());
            assertEquals(TimeDimension.HOUR, result.getDimension());
        }

        @Test
        void subdailyToDay_metadataCorrect() {
            double[] qh = filled(96, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(dt(2025, 6, 15), result.getStart());
            assertEquals(dt(2025, 6, 16), result.getEnd());
            assertEquals(TimeDimension.DAY, result.getDimension());
        }

        @Test
        void dayToMonth_metadataCorrect() {
            double[] days = filled(59, 1.0); // Jan + Feb
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2025, 3, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(dt(2025, 1, 1), result.getStart());
            assertEquals(dt(2025, 3, 1), result.getEnd());
            assertEquals(TimeDimension.MONTH, result.getDimension());
            assertEquals(2, result.size());
        }

        @Test
        void dayToYear_metadataCorrect() {
            double[] days = filled(365, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2026, 1, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(dt(2025, 1, 1), result.getStart());
            assertEquals(dt(2026, 1, 1), result.getEnd());
            assertEquals(TimeDimension.YEAR, result.getDimension());
            assertEquals(1, result.size());
        }

        @Test
        void monthToYear_metadataCorrect() {
            double[] months = filled(12, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2026, 1, 1), months);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.SUM);

            assertEquals(dt(2025, 1, 1), result.getStart());
            assertEquals(dt(2026, 1, 1), result.getEnd());
            assertEquals(TimeDimension.YEAR, result.getDimension());
            assertEquals(1, result.size());
        }
    }

    // ================================================================
    // Aggregation: MIN / MAX
    // ================================================================

    @Nested
    class MinMaxTest {

        @Test
        void qhToH_min() {
            double[] qh = {1.0, 5.0, 3.0, 2.0};
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15), dt(2025, 6, 15, 1, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.MIN);

            assertEquals(1, result.size());
            assertEquals(1.0, result.getValue(0));
        }

        @Test
        void qhToH_max() {
            double[] qh = {1.0, 5.0, 3.0, 2.0};
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15), dt(2025, 6, 15, 1, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.HOUR, AggregationFunction.MAX);

            assertEquals(1, result.size());
            assertEquals(5.0, result.getValue(0));
        }

        @Test
        void subdailyToDay_min() {
            double[] qh = new double[96];
            for (int i = 0; i < 96; i++) qh[i] = i;
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.MIN);

            assertEquals(1, result.size());
            assertEquals(0.0, result.getValue(0));
        }

        @Test
        void subdailyToDay_max() {
            double[] qh = new double[96];
            for (int i = 0; i < 96; i++) qh[i] = i;
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.MAX);

            assertEquals(1, result.size());
            assertEquals(95.0, result.getValue(0));
        }

        @Test
        void dayToMonth_min() {
            double[] days = new double[31]; // Januar
            for (int i = 0; i < 31; i++) days[i] = 10.0 + i;
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2025, 2, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.MIN);

            assertEquals(1, result.size());
            assertEquals(10.0, result.getValue(0));
        }

        @Test
        void dayToMonth_max() {
            double[] days = new double[31]; // Januar
            for (int i = 0; i < 31; i++) days[i] = 10.0 + i;
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 1), dt(2025, 2, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.MAX);

            assertEquals(1, result.size());
            assertEquals(40.0, result.getValue(0));
        }

        @Test
        void monthToYear_min() {
            double[] months = new double[12];
            for (int i = 0; i < 12; i++) months[i] = 100.0 + i * 10;
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2026, 1, 1), months);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.MIN);

            assertEquals(1, result.size());
            assertEquals(100.0, result.getValue(0));
        }

        @Test
        void monthToYear_max() {
            double[] months = new double[12];
            for (int i = 0; i < 12; i++) months[i] = 100.0 + i * 10;
            TimeSeriesSlice src = slice(TimeDimension.MONTH, dt(2025, 1, 1), dt(2026, 1, 1), months);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.MAX);

            assertEquals(1, result.size());
            assertEquals(210.0, result.getValue(0));
        }

        @Test
        void cascadeQhToMonth_min() {
            // 31 Tage, je 96 QH, aufsteigende Werte pro Tag
            double[] qh = new double[31 * 96];
            for (int d = 0; d < 31; d++) {
                Arrays.fill(qh, d * 96, (d + 1) * 96, (double) d);
            }
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 1, 1), dt(2025, 2, 1), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.MIN);

            assertEquals(1, result.size());
            assertEquals(0.0, result.getValue(0));
        }

        @Test
        void cascadeQhToYear_max() {
            // 3 Tage, je 96 QH mit unterschiedlichen Werten
            double[] qh = new double[288];
            Arrays.fill(qh, 0, 96, 1.0);
            Arrays.fill(qh, 96, 192, 5.0);
            Arrays.fill(qh, 192, 288, 3.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 1, 1), dt(2025, 1, 4), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.YEAR, AggregationFunction.MAX);

            assertEquals(1, result.size());
            assertEquals(5.0, result.getValue(0));
        }
    }

    // ================================================================
    // Aggregation: Anschnitt (partieller Start/Ende)
    // ================================================================

    @Nested
    class PartialPeriodTest {

        @Test
        void qhToDay_startAt06_partialFirstDay() {
            // Start 06:00 → 72 QH am ersten Tag (06:00-00:00), 96 am zweiten
            double[] qh = new double[72 + 96];
            Arrays.fill(qh, 0, 72, 1.0);  // Tag 1 (ab 06:00)
            Arrays.fill(qh, 72, 72 + 96, 2.0);  // Tag 2 (voll)
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15, 6, 0), dt(2025, 6, 17), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(2, result.size());
            assertEquals(72.0, result.getValue(0));  // 72 × 1.0
            assertEquals(192.0, result.getValue(1)); // 96 × 2.0
        }

        @Test
        void qhToDay_endAt12_partialLastDay() {
            // Ganzer Tag + halber Tag (00:00-12:00 = 48 QH)
            double[] qh = new double[96 + 48];
            Arrays.fill(qh, 0, 96, 1.0);
            Arrays.fill(qh, 96, 96 + 48, 3.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15), dt(2025, 6, 16, 12, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(2, result.size());
            assertEquals(96.0, result.getValue(0));  // voller Tag
            assertEquals(144.0, result.getValue(1)); // 48 × 3.0
        }

        @Test
        void qhToDay_partialBothEnds() {
            // 06:00 Tag 1 bis 12:00 Tag 2: 72 + 48 = 120 QH
            double[] qh = new double[72 + 48];
            Arrays.fill(qh, 0, 72, 2.0);
            Arrays.fill(qh, 72, 72 + 48, 4.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 6, 15, 6, 0), dt(2025, 6, 16, 12, 0), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(2, result.size());
            assertEquals(144.0, result.getValue(0)); // 72 × 2.0
            assertEquals(192.0, result.getValue(1)); // 48 × 4.0
        }

        @Test
        void hToDay_startAt06_partialFirstDay() {
            // Start 06:00 → 18 H am ersten Tag, 24 am zweiten
            double[] h = new double[18 + 24];
            Arrays.fill(h, 0, 18, 10.0);
            Arrays.fill(h, 18, 18 + 24, 20.0);
            TimeSeriesSlice src = slice(TimeDimension.HOUR,
                    dt(2025, 6, 15, 6, 0), dt(2025, 6, 17), h);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.AVG);

            assertEquals(2, result.size());
            assertEquals(10.0, result.getValue(0));
            assertEquals(20.0, result.getValue(1));
        }

        @Test
        void dayToMonth_startMidMonth() {
            // Start am 15. Januar → 17 Tage im Jan, 28 im Feb = 45 Tage
            double[] days = new double[17 + 28];
            Arrays.fill(days, 0, 17, 1.0);
            Arrays.fill(days, 17, 17 + 28, 2.0);
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 1, 15), dt(2025, 3, 1), days);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.MONTH, AggregationFunction.SUM);

            assertEquals(2, result.size());
            assertEquals(17.0, result.getValue(0));  // Rest-Januar
            assertEquals(56.0, result.getValue(1));  // Feb: 28 × 2.0
        }

        @Test
        void qhToDay_dstDay_startAt06() {
            // DST-Tag 30. März 2025 ab 06:00: 92 - 24 = 68 QH
            int slotsFrom06 = 92 - 24; // 06:00 = 24 QH-Slots offset
            double[] qh = filled(slotsFrom06, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR,
                    dt(2025, 3, 30, 6, 0), dt(2025, 3, 31), qh);

            TimeSeriesSlice result = DimensionConverter.aggregate(src, TimeDimension.DAY, AggregationFunction.SUM);

            assertEquals(1, result.size());
            assertEquals((double) slotsFrom06, result.getValue(0));
        }
    }

    // ================================================================
    // Fehlerfälle
    // ================================================================

    @Nested
    class ErrorCaseTest {

        @Test
        void aggregate_sameDimension_throws() {
            double[] qh = filled(96, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            assertThrows(IllegalArgumentException.class,
                    () -> DimensionConverter.aggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM));
        }

        @Test
        void aggregate_wrongDirection_throws() {
            // Tag → QH ist Disaggregation, nicht Aggregation
            double[] days = {1.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            assertThrows(IllegalArgumentException.class,
                    () -> DimensionConverter.aggregate(src, TimeDimension.QUARTER_HOUR, AggregationFunction.SUM));
        }

        @Test
        void disaggregate_sameDimension_throws() {
            double[] days = {1.0};
            TimeSeriesSlice src = slice(TimeDimension.DAY, dt(2025, 6, 15), dt(2025, 6, 16), days);

            assertThrows(IllegalArgumentException.class,
                    () -> DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM));
        }

        @Test
        void disaggregate_wrongDirection_throws() {
            // QH → Tag ist Aggregation, nicht Disaggregation
            double[] qh = filled(96, 1.0);
            TimeSeriesSlice src = slice(TimeDimension.QUARTER_HOUR, dt(2025, 6, 15), dt(2025, 6, 16), qh);

            assertThrows(IllegalArgumentException.class,
                    () -> DimensionConverter.disaggregate(src, TimeDimension.DAY, AggregationFunction.SUM));
        }

        @Test
        void applyFunction_allNaN_min_returnsNaN() {
            double[] v = {Double.NaN, Double.NaN};
            assertTrue(Double.isNaN(DimensionConverter.applyFunction(v, 0, 2, AggregationFunction.MIN)));
        }

        @Test
        void applyFunction_allNaN_max_returnsNaN() {
            double[] v = {Double.NaN, Double.NaN};
            assertTrue(Double.isNaN(DimensionConverter.applyFunction(v, 0, 2, AggregationFunction.MAX)));
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
