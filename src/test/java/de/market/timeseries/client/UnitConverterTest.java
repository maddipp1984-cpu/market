package de.market.timeseries.client;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class UnitConverterTest {

    private static final double DELTA = 1e-6;

    private static TimeSeriesSlice slice(LocalDateTime start, LocalDateTime end,
                                          TimeDimension dim, double... values) {
        return new TimeSeriesSlice(start, end, dim, values);
    }

    // ================================================================
    // Faktor-Konvertierung
    // ================================================================

    @Nested
    class FaktorKonvertierung {

        @Test
        void kwhToMwh() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    TimeDimension.DAY, 1000.0, 2000.0, 3000.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KWH, Unit.MWH);

            assertEquals(1.0, result.getValues()[0], DELTA);
            assertEquals(2.0, result.getValues()[1], DELTA);
            assertEquals(3.0, result.getValues()[2], DELTA);
        }

        @Test
        void mwhToKwh() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 1.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.MWH, Unit.KWH);

            assertEquals(1000.0, result.getValues()[0], DELTA);
        }

        @Test
        void kwhToGj() {
            // 1 kWh = 0.0036 GJ → 1000 kWh = 3.6 GJ
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 1000.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KWH, Unit.GJ);

            assertEquals(3.6, result.getValues()[0], DELTA);
        }

        @Test
        void kwToMw() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 1, 1, 0),
                    TimeDimension.HOUR, 5000.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KW, Unit.MW);

            assertEquals(5.0, result.getValues()[0], DELTA);
        }

        @Test
        void barToMbar() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 1.013);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.BAR, Unit.MBAR);

            assertEquals(1013.0, result.getValues()[0], DELTA);
        }

        @Test
        void tonneToKg() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 2.5);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.TONNE, Unit.KG);

            assertEquals(2500.0, result.getValues()[0], DELTA);
        }

        @Test
        void m3ToTm3() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 5000.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.M3, Unit.TM3);

            assertEquals(5.0, result.getValues()[0], DELTA);
        }

        @Test
        void nanBleibtNan() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0),
                    TimeDimension.DAY, Double.NaN, 100.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KWH, Unit.MWH);

            assertTrue(Double.isNaN(result.getValues()[0]));
            assertEquals(0.1, result.getValues()[1], DELTA);
        }

        @Test
        void gleicheUnitGibtOriginal() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 42.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KWH, Unit.KWH);

            assertSame(src, result);
        }
    }

    // ================================================================
    // Temperatur (Offset)
    // ================================================================

    @Nested
    class TemperaturKonvertierung {

        @Test
        void celsiusToKelvin() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    TimeDimension.DAY, 0.0, 20.0, 100.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.CELSIUS, Unit.KELVIN);

            assertEquals(273.15, result.getValues()[0], DELTA);
            assertEquals(293.15, result.getValues()[1], DELTA);
            assertEquals(373.15, result.getValues()[2], DELTA);
        }

        @Test
        void kelvinToCelsius() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 273.15);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.KELVIN, Unit.CELSIUS);

            assertEquals(0.0, result.getValues()[0], DELTA);
        }

        @Test
        void negativeTemperaturen() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, -40.0);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.CELSIUS, Unit.KELVIN);

            assertEquals(233.15, result.getValues()[0], DELTA);
        }

        @Test
        void nanBleibtNan() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, Double.NaN);

            TimeSeriesSlice result = UnitConverter.convert(src, Unit.CELSIUS, Unit.KELVIN);

            assertTrue(Double.isNaN(result.getValues()[0]));
        }
    }

    // ================================================================
    // Power → Energy
    // ================================================================

    @Nested
    class PowerToEnergy {

        @Test
        void mwQhToMwh() {
            // 100 MW × 0.25h = 25 MWh pro QH
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR,
                    100.0, 100.0, 100.0, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            for (double v : result.getValues()) {
                assertEquals(25.0, v, DELTA);
            }
        }

        @Test
        void mwHToMwh() {
            // 100 MW × 1h = 100 MWh
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.HOUR, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwDayToMwh_normalerTag() {
            // 100 MW × 24h = 2400 MWh
            LocalDate day = LocalDate.of(2024, 6, 15);
            TimeSeriesSlice src = slice(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    TimeDimension.DAY, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(2400.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwDayToMwh_dstFruehling() {
            // 31.03.2024 = Sommerzeitumstellung → 23 Stunden
            LocalDate day = LocalDate.of(2024, 3, 31);
            TimeSeriesSlice src = slice(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    TimeDimension.DAY, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(2300.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwDayToMwh_dstHerbst() {
            // 27.10.2024 = Winterzeitumstellung → 25 Stunden
            LocalDate day = LocalDate.of(2024, 10, 27);
            TimeSeriesSlice src = slice(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    TimeDimension.DAY, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(2500.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwMonatToMwh() {
            YearMonth ym = YearMonth.of(2024, 1);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            TimeSeriesSlice src = slice(start, end, TimeDimension.MONTH, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(100.0 * UnitConverter.hoursInMonth(ym), result.getValues()[0], DELTA);
        }

        @Test
        void mwJahrToMwh() {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 0, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.YEAR, 100.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertEquals(100.0 * UnitConverter.hoursInYear(2024), result.getValues()[0], DELTA);
        }

        @Test
        void kwToMwh_crossScale() {
            // 1000 kW × 0.25h = 250 kWh = 0.25 MWh
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 15);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR, 1000.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.KW, Unit.MWH);

            assertEquals(0.25, result.getValues()[0], DELTA);
        }

        @Test
        void wToKwh() {
            // 1000 W = 1 kW × 0.25h = 0.25 kWh
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 15);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR, 1000.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.W, Unit.KWH);

            assertEquals(0.25, result.getValues()[0], DELTA);
        }

        @Test
        void kwToGj() {
            // 1000 kW × 1h = 1000 kWh = 3.6 GJ
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.HOUR, 1000.0);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.KW, Unit.GJ);

            assertEquals(3.6, result.getValues()[0], DELTA);
        }

        @Test
        void nanBleibtNan() {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 15);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR, Double.NaN);

            TimeSeriesSlice result = UnitConverter.convertPowerToEnergy(src, Unit.MW, Unit.MWH);

            assertTrue(Double.isNaN(result.getValues()[0]));
        }
    }

    // ================================================================
    // Energy → Power
    // ================================================================

    @Nested
    class EnergyToPower {

        @Test
        void mwhQhToMw() {
            // 25 MWh / 0.25h = 100 MW
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 15);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR, 25.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhHToMw() {
            // 100 MWh / 1h = 100 MW
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.HOUR, 100.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhDayToMw_dstFruehling() {
            // 31.03.2024: 23h → 2300 MWh / 23h = 100 MW
            LocalDate day = LocalDate.of(2024, 3, 31);
            TimeSeriesSlice src = slice(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    TimeDimension.DAY, 2300.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhDayToMw_dstHerbst() {
            // 27.10.2024: 25h → 2500 MWh / 25h = 100 MW
            LocalDate day = LocalDate.of(2024, 10, 27);
            TimeSeriesSlice src = slice(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    TimeDimension.DAY, 2500.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhMonthToMw() {
            // Januar 2024: 744h → 74400 MWh / 744h = 100 MW
            YearMonth ym = YearMonth.of(2024, 1);
            double hours = UnitConverter.hoursInMonth(ym);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            TimeSeriesSlice src = slice(start, end, TimeDimension.MONTH, 100.0 * hours);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhYearToMw() {
            // 2024: 8784h → 878400 MWh / 8784h = 100 MW
            double hours = UnitConverter.hoursInYear(2024);
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 0, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.YEAR, 100.0 * hours);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.MW);

            assertEquals(100.0, result.getValues()[0], DELTA);
        }

        @Test
        void kwhToKw_crossScale() {
            // 250 kWh / 0.25h = 1000 kW
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 0, 15);
            TimeSeriesSlice src = slice(start, end, TimeDimension.QUARTER_HOUR, 250.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.KWH, Unit.KW);

            assertEquals(1000.0, result.getValues()[0], DELTA);
        }

        @Test
        void mwhToKw_crossScale() {
            // 1 MWh / 1h = 1000 kWh / 1h = 1000 kW
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.HOUR, 1.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.MWH, Unit.KW);

            assertEquals(1000.0, result.getValues()[0], DELTA);
        }

        @Test
        void gwhToMw() {
            // 1 GWh / 1h = 1_000_000 kWh / 1h = 1_000_000 kW = 1000 MW
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 1, 1, 0);
            TimeSeriesSlice src = slice(start, end, TimeDimension.HOUR, 1.0);

            TimeSeriesSlice result = UnitConverter.convertEnergyToPower(src, Unit.GWH, Unit.MW);

            assertEquals(1000.0, result.getValues()[0], DELTA);
        }
    }

    // ================================================================
    // Nicht konvertierbar
    // ================================================================

    @Nested
    class NichtKonvertierbar {

        @Test
        void nm3ZuM3_fehler() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 100.0);

            assertThrows(IllegalArgumentException.class,
                    () -> UnitConverter.convert(src, Unit.NM3, Unit.M3));
        }

        @Test
        void kwhZuBar_fehler() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 100.0);

            assertThrows(IllegalArgumentException.class,
                    () -> UnitConverter.convert(src, Unit.KWH, Unit.BAR));
        }

        @Test
        void noneKategorie_fehler() {
            TimeSeriesSlice src = slice(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 2, 0, 0),
                    TimeDimension.DAY, 50.0);

            assertThrows(IllegalArgumentException.class,
                    () -> UnitConverter.convert(src, Unit.PERCENT, Unit.KWH));
        }
    }

    // ================================================================
    // Unit-Enum: isConvertibleTo / isCrossDomainConvertibleTo
    // ================================================================

    @Nested
    class UnitEnumTests {

        @Test
        void gleicheKategorie_konvertierbar() {
            assertTrue(Unit.KWH.isConvertibleTo(Unit.MWH));
            assertTrue(Unit.BAR.isConvertibleTo(Unit.MBAR));
            assertTrue(Unit.CELSIUS.isConvertibleTo(Unit.KELVIN));
        }

        @Test
        void verschiedeneKategorien_nichtKonvertierbar() {
            assertFalse(Unit.KWH.isConvertibleTo(Unit.BAR));
            assertFalse(Unit.KW.isConvertibleTo(Unit.MWH));
            assertFalse(Unit.NM3.isConvertibleTo(Unit.M3));
        }

        @Test
        void noneKategorie_nichtKonvertierbar() {
            assertFalse(Unit.PERCENT.isConvertibleTo(Unit.HOURS));
            assertFalse(Unit.NONE.isConvertibleTo(Unit.KWH));
        }

        @Test
        void crossDomain_powerEnergy() {
            assertTrue(Unit.MW.isCrossDomainConvertibleTo(Unit.MWH));
            assertTrue(Unit.KW.isCrossDomainConvertibleTo(Unit.GWH));
            assertTrue(Unit.MWH.isCrossDomainConvertibleTo(Unit.KW));
        }

        @Test
        void crossDomain_apparentPowerNicht() {
            assertFalse(Unit.KVA.isCrossDomainConvertibleTo(Unit.KWH));
            assertFalse(Unit.MVAR.isCrossDomainConvertibleTo(Unit.MWH));
        }

        @Test
        void gleicheUnit_konvertierbar() {
            assertTrue(Unit.KWH.isConvertibleTo(Unit.KWH));
        }
    }

    // ================================================================
    // Stundenberechnungen
    // ================================================================

    @Nested
    class Stundenberechnungen {

        @Test
        void normalerTag_24h() {
            assertEquals(24.0, UnitConverter.hoursInDay(LocalDate.of(2024, 6, 15)), DELTA);
        }

        @Test
        void sommerzeitumstellung_23h() {
            assertEquals(23.0, UnitConverter.hoursInDay(LocalDate.of(2024, 3, 31)), DELTA);
        }

        @Test
        void winterzeitumstellung_25h() {
            assertEquals(25.0, UnitConverter.hoursInDay(LocalDate.of(2024, 10, 27)), DELTA);
        }

        @Test
        void januarStunden() {
            assertEquals(744.0, UnitConverter.hoursInMonth(YearMonth.of(2024, 1)), DELTA);
        }

        @Test
        void maerzStunden_mitDst() {
            // März 2024: 31 Tage, davon einer mit 23h → 31×24 - 1 = 743h
            assertEquals(743.0, UnitConverter.hoursInMonth(YearMonth.of(2024, 3)), DELTA);
        }

        @Test
        void oktoberStunden_mitDst() {
            // Oktober 2024: 31 Tage, davon einer mit 25h → 31×24 + 1 = 745h
            assertEquals(745.0, UnitConverter.hoursInMonth(YearMonth.of(2024, 10)), DELTA);
        }

        @Test
        void schaltjahr2024() {
            assertEquals(8784.0, UnitConverter.hoursInYear(2024), DELTA);
        }

        @Test
        void normalJahr2023() {
            assertEquals(8760.0, UnitConverter.hoursInYear(2023), DELTA);
        }
    }
}
