package de.market.timeseries.client;

import de.market.timeseries.api.TimeSeriesService;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSeriesClientWriteTest {

    private static final double DELTA = 1e-6;
    private static final long TS_ID = 1L;

    @Mock
    private TimeSeriesService service;

    private TimeSeriesClient client;

    @BeforeEach
    void setUp() {
        client = new TimeSeriesClient(service);
    }

    private TimeSeriesHeader header(TimeDimension dim) {
        TimeSeriesHeader h = new TimeSeriesHeader("TEST", dim, Unit.KWH);
        h.setTsId(TS_ID);
        return h;
    }

    // ================================================================
    // Gleiche Dimension — direkt durchschreiben
    // ================================================================

    @Nested
    class GleicheDimension {

        @Test
        void qhSlice_zuQhDb_direktWriteDay() throws SQLException {
            // 1 Tag QH-Daten → sollte direkt 1× writeDay aufrufen
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);
            double[] values = new double[96];
            Arrays.fill(values, 42.0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, values));

            verify(service, times(1)).writeDay(eq(TS_ID),
                    eq(LocalDate.of(2024, 6, 15)), any(double[].class));
        }

        @Test
        void tagesSlice_zuTagesDb_writeSimpleProTag() throws SQLException {
            // 3 Tageswerte → 3× writeSimple
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 4, 0, 0);
            double[] values = {100.0, 200.0, 300.0};

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.DAY)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, values));

            verify(service).writeSimple(TS_ID, LocalDate.of(2024, 1, 1), 100.0);
            verify(service).writeSimple(TS_ID, LocalDate.of(2024, 1, 2), 200.0);
            verify(service).writeSimple(TS_ID, LocalDate.of(2024, 1, 3), 300.0);
        }

        @Test
        void jahresSlice_zuJahresDb_writeSimpleProJahr() throws SQLException {
            LocalDateTime start = LocalDateTime.of(2023, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 1, 1, 0, 0);
            double[] values = {1000.0, 2000.0, 3000.0};

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.YEAR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.YEAR, values));

            verify(service).writeSimple(TS_ID, 2023, 1000.0);
            verify(service).writeSimple(TS_ID, 2024, 2000.0);
            verify(service).writeSimple(TS_ID, 2025, 3000.0);
        }

        @Test
        void zweiTage_qh_splittetKorrekt() throws SQLException {
            // 2 Tage QH → 2× writeDay
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 17, 0, 0);
            double[] values = new double[192]; // 2 × 96
            Arrays.fill(values, 0, 96, 10.0);
            Arrays.fill(values, 96, 192, 20.0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, values));

            ArgumentCaptor<double[]> captor = ArgumentCaptor.forClass(double[].class);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(service, times(2)).writeDay(eq(TS_ID), dateCaptor.capture(), captor.capture());

            List<LocalDate> dates = dateCaptor.getAllValues();
            List<double[]> dayArrays = captor.getAllValues();

            assertEquals(LocalDate.of(2024, 6, 15), dates.get(0));
            assertEquals(96, dayArrays.get(0).length);
            assertEquals(10.0, dayArrays.get(0)[0], DELTA);

            assertEquals(LocalDate.of(2024, 6, 16), dates.get(1));
            assertEquals(96, dayArrays.get(1).length);
            assertEquals(20.0, dayArrays.get(1)[0], DELTA);
        }
    }

    // ================================================================
    // Disaggregation: Grob → Fein
    // ================================================================

    @Nested
    class Disaggregation {

        @Test
        void tagSlice_zuQhDb_disaggregiert() throws SQLException {
            // 1 Tageswert 2400 kWh → 96 QH à 25 kWh (Gleichverteilung)
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, new double[]{2400.0}));

            ArgumentCaptor<double[]> captor = ArgumentCaptor.forClass(double[].class);
            verify(service).writeDay(eq(TS_ID), eq(LocalDate.of(2024, 6, 15)),
                    captor.capture());

            double[] written = captor.getValue();
            assertEquals(96, written.length);
            assertEquals(25.0, written[0], DELTA); // 2400 / 96
            assertEquals(25.0, written[95], DELTA);
        }

        @Test
        void tagSlice_zuQhDb_dstFruehling() throws SQLException {
            // 31.03.2024 (Sommerzeit): 92 QH statt 96
            // 2300 kWh → 92 QH à 25 kWh
            LocalDateTime start = LocalDateTime.of(2024, 3, 31, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 4, 1, 0, 0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, new double[]{2300.0}));

            ArgumentCaptor<double[]> captor = ArgumentCaptor.forClass(double[].class);
            verify(service).writeDay(eq(TS_ID), eq(LocalDate.of(2024, 3, 31)),
                    captor.capture());

            double[] written = captor.getValue();
            assertEquals(92, written.length); // DST!
            assertEquals(25.0, written[0], DELTA); // 2300 / 92
        }

        @Test
        void tagSlice_zuQhDb_dstHerbst() throws SQLException {
            // 27.10.2024 (Winterzeit): 100 QH statt 96
            // 2500 kWh → 100 QH à 25 kWh
            LocalDateTime start = LocalDateTime.of(2024, 10, 27, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 10, 28, 0, 0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, new double[]{2500.0}));

            ArgumentCaptor<double[]> captor = ArgumentCaptor.forClass(double[].class);
            verify(service).writeDay(eq(TS_ID), eq(LocalDate.of(2024, 10, 27)),
                    captor.capture());

            double[] written = captor.getValue();
            assertEquals(100, written.length); // DST!
            assertEquals(25.0, written[0], DELTA); // 2500 / 100
        }

        @Test
        void jahrSlice_zuQhDb_kaskadiert() throws SQLException {
            // 1 Jahreswert → disaggregiert über YEAR→MONTH→DAY→QH
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 0, 0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.YEAR, new double[]{876000.0}));

            // 2024 ist Schaltjahr = 366 Tage → 366 writeDay-Aufrufe
            verify(service, times(366)).writeDay(eq(TS_ID), any(LocalDate.class),
                    any(double[].class));
        }
    }

    // ================================================================
    // Aggregation: Fein → Grob
    // ================================================================

    @Nested
    class Aggregation {

        @Test
        void qhSlice_zuTagDb_aggregiert() throws SQLException {
            // 96 QH à 25 kWh → SUM = 2400 kWh → 1× writeSimple
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);
            double[] values = new double[96];
            Arrays.fill(values, 25.0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.DAY)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, values));

            ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
            verify(service).writeSimple(eq(TS_ID), eq(LocalDate.of(2024, 6, 15)),
                    valueCaptor.capture());

            assertEquals(2400.0, valueCaptor.getValue(), DELTA);
        }

        @Test
        void qhSlice_zuMonatDb_aggregiert() throws SQLException {
            // Juni 2024: 30 Tage × 96 QH = 2880 Werte → 1 Monatswert
            LocalDateTime start = LocalDateTime.of(2024, 6, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 7, 1, 0, 0);
            int totalSlots = 30 * 96; // Keine DST-Tage im Juni
            double[] values = new double[totalSlots];
            Arrays.fill(values, 10.0);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.MONTH)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, values));

            ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
            verify(service).writeSimple(eq(TS_ID), eq(LocalDate.of(2024, 6, 1)),
                    valueCaptor.capture());

            assertEquals(totalSlots * 10.0, valueCaptor.getValue(), DELTA);
        }
    }

    // ================================================================
    // NaN-Behandlung
    // ================================================================

    @Nested
    class NanBehandlung {

        @Test
        void nanWerteWerdenDurchgereicht() throws SQLException {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 4, 0, 0);
            double[] values = {100.0, Double.NaN, 300.0};

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.DAY)));

            client.write(TS_ID, new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, values));

            verify(service).writeSimple(TS_ID, LocalDate.of(2024, 1, 1), 100.0);
            verify(service).writeSimple(TS_ID, LocalDate.of(2024, 1, 3), 300.0);

            // NaN separat prüfen per ArgumentCaptor
            ArgumentCaptor<Double> captor = ArgumentCaptor.forClass(Double.class);
            verify(service, times(3)).writeSimple(eq(TS_ID), any(LocalDate.class), captor.capture());

            List<Double> allValues = captor.getAllValues();
            assertEquals(100.0, allValues.get(0), DELTA);
            assertTrue(Double.isNaN(allValues.get(1)));
            assertEquals(300.0, allValues.get(2), DELTA);
        }
    }

    // ================================================================
    // Fehlerfall
    // ================================================================

    @Nested
    class Fehlerfaelle {

        @Test
        void unbekannteZeitreihe_wirftFehler() throws SQLException {
            when(service.getHeader(TS_ID)).thenReturn(Optional.empty());

            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 2, 0, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> client.write(TS_ID, new TimeSeriesSlice(start, end,
                            TimeDimension.DAY, new double[]{100.0})));
        }
    }
}
