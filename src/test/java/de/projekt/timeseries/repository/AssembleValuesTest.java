package de.projekt.timeseries.repository;

import de.projekt.timeseries.model.TimeDimension;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssembleValuesTest {

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
        java.util.Arrays.fill(arr, value);
        return arr;
    }

    private static boolean allNaN(double[] arr, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Double.isNaN(arr[i])) return false;
        }
        return true;
    }

    private static LocalDate lastDayExcl(LocalDateTime end) {
        LocalDate d = end.toLocalDate();
        return end.toLocalTime().equals(LocalTime.MIDNIGHT) ? d : d.plusDays(1);
    }

    private static double[] assembleValues(Map<LocalDate, double[]> dayValues, TimeDimension dim,
                                           LocalDateTime start, LocalDateTime end) {
        return TimeSeriesRepository.assembleValues(dayValues, dim, start, end, lastDayExcl(end));
    }

    // ================================================================
    // Ganztägig, keine Lücken
    // ================================================================

    @Test
    void fullDay_15min_returns96Values() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16));

        assertEquals(96, result.length);
        assertEquals(1.0, result[0]);
        assertEquals(1.0, result[95]);
    }

    @Test
    void fullDay_1h_returns24Values() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(24, 2.0));

        double[] result = assembleValues(
                data, TimeDimension.HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16));

        assertEquals(24, result.length);
        assertEquals(2.0, result[0]);
    }

    // ================================================================
    // Mehrere Tage vollständig
    // ================================================================

    @Test
    void threeDays_15min_returns288Values() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));
        data.put(LocalDate.of(2025, 6, 16), filled(96, 2.0));
        data.put(LocalDate.of(2025, 6, 17), filled(96, 3.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 18));

        assertEquals(288, result.length);
        assertEquals(1.0, result[0]);   // Tag 1
        assertEquals(2.0, result[96]);  // Tag 2
        assertEquals(3.0, result[192]); // Tag 3
    }

    // ================================================================
    // Fehlende Tage → NaN-Auffüllung
    // ================================================================

    @Test
    void missingDayInMiddle_filledWithNaN() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));
        // 16. Juni fehlt
        data.put(LocalDate.of(2025, 6, 17), filled(96, 3.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 18));

        assertEquals(288, result.length);
        assertEquals(1.0, result[0]);
        assertTrue(allNaN(result, 96, 192), "Fehlender Tag muss NaN sein");
        assertEquals(3.0, result[192]);
    }

    @Test
    void allDaysMissing_allNaN() {
        Map<LocalDate, double[]> data = new HashMap<>();

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 18));

        assertEquals(288, result.length);
        assertTrue(allNaN(result, 0, 288));
    }

    @Test
    void firstDaysMissing_valuesStartLater() {
        Map<LocalDate, double[]> data = new HashMap<>();
        // 01.01 und 02.01 fehlen
        data.put(LocalDate.of(2025, 1, 3), filled(96, 5.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 1, 1), dt(2025, 1, 4));

        assertEquals(288, result.length);
        assertTrue(allNaN(result, 0, 192), "Erste zwei Tage NaN");
        assertEquals(5.0, result[192]);
    }

    // ================================================================
    // Anschnitt: Start nicht Mitternacht
    // ================================================================

    @Test
    void startAt0600_15min_cuts24SlotsFromFirstDay() {
        Map<LocalDate, double[]> data = new HashMap<>();
        double[] day = new double[96];
        for (int i = 0; i < 96; i++) day[i] = i;
        data.put(LocalDate.of(2025, 6, 15), day);

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 16));

        assertEquals(72, result.length);       // 96 - 24
        assertEquals(24.0, result[0]);          // Slot 24 = 06:00
        assertEquals(95.0, result[71]);         // Letzter Slot des Tages
    }

    @Test
    void startAt0600_1h_cuts6SlotsFromFirstDay() {
        Map<LocalDate, double[]> data = new HashMap<>();
        double[] day = new double[24];
        for (int i = 0; i < 24; i++) day[i] = i;
        data.put(LocalDate.of(2025, 6, 15), day);

        double[] result = assembleValues(
                data, TimeDimension.HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 16));

        assertEquals(18, result.length);
        assertEquals(6.0, result[0]);
    }

    // ================================================================
    // Anschnitt: Ende nicht Mitternacht
    // ================================================================

    @Test
    void endAt1800_15min_cutsLastDay() {
        Map<LocalDate, double[]> data = new HashMap<>();
        double[] day = new double[96];
        for (int i = 0; i < 96; i++) day[i] = i;
        data.put(LocalDate.of(2025, 6, 15), day);

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 15, 18, 0));

        assertEquals(72, result.length);        // Slots 0-71 (00:00 bis 17:45)
        assertEquals(0.0, result[0]);
        assertEquals(71.0, result[71]);
    }

    // ================================================================
    // Anschnitt: Start UND Ende nicht Mitternacht
    // ================================================================

    @Test
    void startAndEnd_sameDay_15min() {
        Map<LocalDate, double[]> data = new HashMap<>();
        double[] day = new double[96];
        for (int i = 0; i < 96; i++) day[i] = i;
        data.put(LocalDate.of(2025, 6, 15), day);

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 15, 18, 0));

        assertEquals(48, result.length);        // 06:00 bis 17:45 = 48 Slots
        assertEquals(24.0, result[0]);           // Slot 24 = 06:00
        assertEquals(71.0, result[47]);          // Slot 71 = 17:45
    }

    @Test
    void startAndEnd_differentDays_bothCut() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));
        data.put(LocalDate.of(2025, 6, 16), filled(96, 2.0));
        data.put(LocalDate.of(2025, 6, 17), filled(96, 3.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 17, 18, 0));

        // Tag 1: 72 Slots (06:00-24:00), Tag 2: 96, Tag 3: 72 (00:00-18:00)
        assertEquals(240, result.length);
        assertEquals(1.0, result[0]);     // Tag 1 ab 06:00
        assertEquals(2.0, result[72]);    // Tag 2 vollständig
        assertEquals(3.0, result[168]);   // Tag 3 bis 18:00
    }

    // ================================================================
    // Anschnitt + fehlende Tage kombiniert
    // ================================================================

    @Test
    void startCut_missingFirstDay_fillsNaN() {
        Map<LocalDate, double[]> data = new HashMap<>();
        // Tag 15 fehlt
        data.put(LocalDate.of(2025, 6, 16), filled(96, 2.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 17));

        // Tag 1: 72 NaN (06:00-24:00), Tag 2: 96 Werte
        assertEquals(168, result.length);
        assertTrue(allNaN(result, 0, 72), "Angeschnittener fehlender Tag muss NaN sein");
        assertEquals(2.0, result[72]);
    }

    @Test
    void endCut_missingLastDay_fillsNaN() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));
        // Tag 16 fehlt

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16, 18, 0));

        // Tag 1: 96 Werte, Tag 2: 72 NaN (00:00-18:00)
        assertEquals(168, result.length);
        assertEquals(1.0, result[0]);
        assertTrue(allNaN(result, 96, 168), "Angeschnittener fehlender letzter Tag muss NaN sein");
    }

    // ================================================================
    // DST: Sommerzeit (März) — 23h = 92 QH
    // ================================================================

    @Test
    void dstSpringForward_15min_92Slots() {
        // 30. März 2025 = Sommerzeitumstellung (Europe/Berlin)
        LocalDate dstDay = LocalDate.of(2025, 3, 30);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(92, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 3, 30), dt(2025, 3, 31));

        assertEquals(92, result.length);
    }

    @Test
    void dstSpringForward_missingDay_fills92NaN() {
        LocalDate dstDay = LocalDate.of(2025, 3, 30);
        Map<LocalDate, double[]> data = new HashMap<>();

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 3, 30), dt(2025, 3, 31));

        assertEquals(92, result.length);
        assertTrue(allNaN(result, 0, 92));
    }

    // ================================================================
    // DST: Winterzeit (Oktober) — 25h = 100 QH
    // ================================================================

    @Test
    void dstFallBack_15min_100Slots() {
        // 26. Oktober 2025 = Winterzeitumstellung (Europe/Berlin)
        LocalDate dstDay = LocalDate.of(2025, 10, 26);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(100, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 10, 26), dt(2025, 10, 27));

        assertEquals(100, result.length);
    }

    @Test
    void dstFallBack_missingDay_fills100NaN() {
        LocalDate dstDay = LocalDate.of(2025, 10, 26);
        Map<LocalDate, double[]> data = new HashMap<>();

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 10, 26), dt(2025, 10, 27));

        assertEquals(100, result.length);
        assertTrue(allNaN(result, 0, 100));
    }

    // ================================================================
    // DST + Anschnitt kombiniert
    // ================================================================

    @Test
    void dstSpringForward_startAt0600_correctSlotCount() {
        // 30. März: 02:00→03:00 übersprungen, 00:00 bis 06:00 = 5h = 20 Slots
        LocalDate dstDay = LocalDate.of(2025, 3, 30);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(92, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 3, 30, 6, 0), dt(2025, 3, 31));

        assertEquals(92 - 20, result.length); // 72 Slots ab 06:00
    }

    @Test
    void dstFallBack_endAt1800_correctSlotCount() {
        // 26. Oktober: 25h-Tag, 00:00 bis 18:00 = 18h + 1h extra = 76 Slots
        LocalDate dstDay = LocalDate.of(2025, 10, 26);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(100, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 10, 26), dt(2025, 10, 26, 18, 0));

        // 00:00 CEST bis 18:00 CET = 19h = 76 QH
        assertEquals(76, result.length);
    }

    @Test
    void dstFallBack_startAt0600_correctSlotCount() {
        // 26. Oktober: 00:00 CEST bis 06:00 CET = 7h = 28 Slots (wegen extra Stunde)
        LocalDate dstDay = LocalDate.of(2025, 10, 26);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(100, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 10, 26, 6, 0), dt(2025, 10, 27));

        assertEquals(100 - 28, result.length); // 72 Slots
    }

    // ================================================================
    // DST: 1h-Dimension
    // ================================================================

    @Test
    void dstSpringForward_1h_23Slots() {
        LocalDate dstDay = LocalDate.of(2025, 3, 30);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(23, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.HOUR,
                dt(2025, 3, 30), dt(2025, 3, 31));

        assertEquals(23, result.length);
    }

    @Test
    void dstFallBack_1h_25Slots() {
        LocalDate dstDay = LocalDate.of(2025, 10, 26);
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(dstDay, filled(25, 1.0));

        double[] result = assembleValues(
                data, TimeDimension.HOUR,
                dt(2025, 10, 26), dt(2025, 10, 27));

        assertEquals(25, result.length);
    }

    // ================================================================
    // Mehrtägig über DST-Grenze
    // ================================================================

    @Test
    void rangeAcrossDstSpring_correctTotalSlots() {
        // 29. März: 96, 30. März: 92, 31. März: 96 = 284
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 3, 29), filled(96, 1.0));
        data.put(LocalDate.of(2025, 3, 30), filled(92, 2.0));
        data.put(LocalDate.of(2025, 3, 31), filled(96, 3.0));

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 3, 29), dt(2025, 4, 1));

        assertEquals(284, result.length);
        assertEquals(1.0, result[0]);
        assertEquals(2.0, result[96]);
        assertEquals(3.0, result[188]); // 96 + 92
    }

    // ================================================================
    // Ende exakt Mitternacht
    // ================================================================

    @Test
    void endAtMidnight_excludesThatDay() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(96, 1.0));
        data.put(LocalDate.of(2025, 6, 16), filled(96, 2.0));

        // end = 16. Juni 00:00 → 16. Juni wird NICHT eingeschlossen
        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16, 0, 0));

        assertEquals(96, result.length);
        assertEquals(1.0, result[0]);
    }

    // ================================================================
    // Leeres Array (korrupte Daten)
    // ================================================================

    @Test
    void emptyArray_treatedAsMissing() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), new double[0]);

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16));

        assertEquals(96, result.length);
        assertTrue(allNaN(result, 0, 96));
    }

    // ================================================================
    // Korrupte Daten + Anschnitt
    // ================================================================

    @Test
    void shortArray_withStartCut_paddedCorrectly() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(50, 1.0)); // statt 96

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15, 6, 0), dt(2025, 6, 16));

        // Padded auf 96, dann Slots 24-95 = 72 Werte
        assertEquals(72, result.length);
        assertEquals(1.0, result[0]);    // Slot 24 hat noch Wert (50 > 24)
        assertEquals(1.0, result[25]);   // Slot 49 = letzter Wert
        assertTrue(allNaN(result, 26, 72), "Ab Slot 50 NaN");
    }

    // ================================================================
    // Korrupte Daten: Array-Länge stimmt nicht
    // ================================================================

    @Test
    void shortArray_paddedWithNaN() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(50, 1.0)); // statt 96

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16));

        assertEquals(96, result.length);
        assertEquals(1.0, result[0]);
        assertEquals(1.0, result[49]);
        assertTrue(allNaN(result, 50, 96), "Rest muss NaN sein");
    }

    @Test
    void longArray_truncated() {
        Map<LocalDate, double[]> data = new HashMap<>();
        data.put(LocalDate.of(2025, 6, 15), filled(120, 1.0)); // statt 96

        double[] result = assembleValues(
                data, TimeDimension.QUARTER_HOUR,
                dt(2025, 6, 15), dt(2025, 6, 16));

        assertEquals(96, result.length);
        assertEquals(1.0, result[0]);
        assertEquals(1.0, result[95]);
    }
}
