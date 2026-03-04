package de.projekt.timeseries.client;

import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TimeSeriesSlice;
import de.projekt.timeseries.model.Unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSeriesClientTest {

    private static final double DELTA = 1e-6;
    private static final long TS_ID = 1L;

    @Mock
    private TimeSeriesService service;

    private TimeSeriesClient client;

    @BeforeEach
    void setUp() {
        client = new TimeSeriesClient(service);
    }

    private TimeSeriesHeader header(TimeDimension dim, Unit unit) {
        TimeSeriesHeader h = new TimeSeriesHeader("TEST", dim, unit);
        h.setTsId(TS_ID);
        return h;
    }

    // ================================================================
    // Reihenfolge: Power→Energy VOR Aggregation
    // ================================================================

    @Nested
    class PowerToEnergyVorAggregation {

        @Test
        void mwQh_zu_mwhTag_unitZuerstDannAggregation() throws SQLException {
            // 96 QH-Werte à 100 MW
            // Korrekte Reihenfolge: 100 MW × 0.25h = 25 MWh pro QH, dann SUM(96 × 25) = 2400
            // Falsche Reihenfolge (Dim zuerst): SUM(96 × 100) = 9600, dann × h = falsch
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0); // Normaltag
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);

            double[] qhValues = new double[96];
            java.util.Arrays.fill(qhValues, 100.0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, qhValues);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR, Unit.MW)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end,
                    TimeDimension.DAY, AggregationFunction.SUM, Unit.MWH);

            assertEquals(1, result.size());
            assertEquals(2400.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwQh_zu_mwhTag_dstFruehling() throws SQLException {
            // 31.03.2024: 92 QH × 100 MW × 0.25h = 2300 MWh
            LocalDateTime start = LocalDateTime.of(2024, 3, 31, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 4, 1, 0, 0);

            double[] qhValues = new double[92]; // DST: 23h × 4 = 92
            java.util.Arrays.fill(qhValues, 100.0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, qhValues);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR, Unit.MW)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end,
                    TimeDimension.DAY, AggregationFunction.SUM, Unit.MWH);

            assertEquals(1, result.size());
            assertEquals(2300.0, result.getValues()[0], DELTA);
        }
    }

    // ================================================================
    // Reihenfolge: Aggregation VOR Energy→Power
    // ================================================================

    @Nested
    class AggregationVorEnergyToPower {

        @Test
        void mwhQh_zu_mwTag_dimensionZuerstDannUnit() throws SQLException {
            // 96 QH à 25 MWh → SUM = 2400 MWh → / 24h = 100 MW
            // Korrekte Reihenfolge: Dimension zuerst (SUM), dann ÷ Stunden
            // Falsche Reihenfolge (Unit zuerst): 25/0.25=100 pro QH, SUM(96×100)=9600 → falsch
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);

            double[] qhValues = new double[96];
            java.util.Arrays.fill(qhValues, 25.0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, qhValues);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR, Unit.MWH)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end,
                    TimeDimension.DAY, AggregationFunction.SUM, Unit.MW);

            assertEquals(1, result.size());
            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhQh_zu_mwTag_dstHerbst() throws SQLException {
            // 27.10.2024: 100 QH à 25 MWh → SUM = 2500 MWh → / 25h = 100 MW
            LocalDateTime start = LocalDateTime.of(2024, 10, 27, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 10, 28, 0, 0);

            double[] qhValues = new double[100]; // DST: 25h × 4 = 100
            java.util.Arrays.fill(qhValues, 25.0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, qhValues);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR, Unit.MWH)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end,
                    TimeDimension.DAY, AggregationFunction.SUM, Unit.MW);

            assertEquals(1, result.size());
            assertEquals(100.0, result.getValues()[0], DELTA);
        }
    }

    // ================================================================
    // Nur Unit-Konvertierung (keine Dimension)
    // ================================================================

    @Nested
    class NurUnitKonvertierung {

        @Test
        void kwhToMwh_ohneDimensionsaenderung() throws SQLException {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 2, 0, 0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.DAY, new double[]{1000.0, 2000.0});

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.DAY, Unit.KWH)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end, Unit.MWH);

            assertEquals(2, result.size());
            assertEquals(1.0, result.getValues()[0], DELTA);
            assertEquals(2.0, result.getValues()[1], DELTA);
        }

        @Test
        void mwToMwh_powerToEnergy() throws SQLException {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.HOUR, new double[]{100.0});

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.HOUR, Unit.MW)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end, Unit.MWH);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }
    }

    // ================================================================
    // Nicht konvertierbar → Fehler
    // ================================================================

    @Nested
    class NichtKonvertierbarFehler {

        @Test
        void kvaZuKwh_fehler() throws SQLException {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.HOUR, new double[]{100.0});

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.HOUR, Unit.KVA)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            assertThrows(IllegalArgumentException.class,
                    () -> client.read(TS_ID, start, end, Unit.KWH));
        }
    }

    // ================================================================
    // Faktor + Aggregation
    // ================================================================

    @Nested
    class FaktorMitAggregation {

        @Test
        void kwhQh_zu_mwhTag() throws SQLException {
            // Faktor-Konvertierung: Reihenfolge egal, aber Unit zuerst (konsistent)
            // 96 QH à 100 kWh → Unit: 96 × 0.1 MWh → SUM = 9.6 MWh
            LocalDateTime start = LocalDateTime.of(2024, 6, 15, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 16, 0, 0);

            double[] qhValues = new double[96];
            java.util.Arrays.fill(qhValues, 100.0);
            TimeSeriesSlice rawSlice = new TimeSeriesSlice(start, end,
                    TimeDimension.QUARTER_HOUR, qhValues);

            when(service.getHeader(TS_ID)).thenReturn(Optional.of(
                    header(TimeDimension.QUARTER_HOUR, Unit.KWH)));
            when(service.read(TS_ID, start, end)).thenReturn(rawSlice);

            TimeSeriesSlice result = client.read(TS_ID, start, end,
                    TimeDimension.DAY, AggregationFunction.SUM, Unit.MWH);

            assertEquals(1, result.size());
            assertEquals(9.6, result.getValues()[0], DELTA);
        }
    }
}
