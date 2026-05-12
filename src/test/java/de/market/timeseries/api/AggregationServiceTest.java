package de.market.timeseries.api;

import de.market.timeseries.client.AggregationFunction;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;
import de.market.timeseries.repository.HeaderRepository;
import de.market.timeseries.repository.TimeSeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock private TimeSeriesRepository tsRepo;
    @Mock private HeaderRepository headerRepo;
    @Mock private de.market.timeseries.client.TimeSeriesClient client;

    private AggregationService service;

    @BeforeEach
    void setUp() {
        service = new AggregationService(client, tsRepo, headerRepo);
    }

    // ---- Input validation ----

    @Test
    void shouldThrowWhenLessThanTwoSeries() {
        assertThatThrownBy(() -> service.aggregate(List.of(1L), dt(2024, 1, 1), dt(2024, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mindestens 2");
    }

    @Test
    void shouldThrowWhenEndNotAfterStart() {
        assertThatThrownBy(() -> service.aggregate(List.of(1L, 2L), dt(2024, 12, 31), dt(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end muss nach start liegen");
    }

    // ---- SQL Shortcut ----

    @Test
    void shouldUseSqlShortcutForSameDimSameUnit() {
        LocalDateTime start = dt(2024, 1, 1);
        LocalDateTime end = dt(2024, 1, 31);

        TimeSeriesHeader h1 = header(1L, "ZR1", TimeDimension.DAY, Unit.KWH);
        TimeSeriesHeader h2 = header(2L, "ZR2", TimeDimension.DAY, Unit.KWH);
        when(headerRepo.findByIds(List.of(1L, 2L))).thenReturn(List.of(h1, h2));

        TimeSeriesSlice sumSlice = new TimeSeriesSlice(start, end, TimeDimension.DAY, new double[]{100, 200});
        when(tsRepo.readSumSimple(eq(List.of(1L, 2L)), eq(TimeDimension.DAY), eq(start), eq(end)))
                .thenReturn(sumSlice);

        AggregationService.AggregationResult result = service.aggregate(List.of(1L, 2L), start, end);

        assertThat(result.targetDimension()).isEqualTo(TimeDimension.DAY);
        assertThat(result.targetUnit()).isEqualTo(Unit.KWH);
        assertThat(result.sumSlice().getValues()).containsExactly(100d, 200d);
    }

    // ---- Non-convertible units ----

    @Test
    void shouldThrowWhenUnitsNotConvertible() {
        TimeSeriesHeader h1 = header(1L, "ZR1", TimeDimension.DAY, Unit.KWH);
        TimeSeriesHeader h2 = header(2L, "ZR2", TimeDimension.DAY, Unit.KVA);
        when(headerRepo.findByIds(List.of(1L, 2L))).thenReturn(List.of(h1, h2));

        assertThatThrownBy(() -> service.aggregate(List.of(1L, 2L), dt(2024, 1, 1), dt(2024, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht konvertierbar");
    }

    // ---- Mixed dimensions ----

    @Test
    void shouldHandleMixedDimensionsInGroupedPath() {
        LocalDateTime start = dt(2024, 1, 1);
        LocalDateTime end = dt(2024, 1, 2);

        TimeSeriesHeader dayHeader = header(1L, "ZR1", TimeDimension.DAY, Unit.KWH);
        TimeSeriesHeader monthHeader = header(2L, "ZR2", TimeDimension.MONTH, Unit.KWH);
        when(headerRepo.findByIds(List.of(1L, 2L))).thenReturn(List.of(dayHeader, monthHeader));

        // DAY group: read 31 values, disaggregated to DAY (no change since targetDim=min=DAY)
        TimeSeriesSlice daySlice = new TimeSeriesSlice(start, end, TimeDimension.DAY, new double[]{50, 0});
        when(tsRepo.readSumSimple(eq(List.of(1L)), eq(TimeDimension.DAY), eq(start), eq(end)))
                .thenReturn(daySlice);

        // MONTH group: read 1 monthly value, disaggregated to DAY
        TimeSeriesSlice monthSlice = new TimeSeriesSlice(dt(2024, 1, 1), dt(2024, 2, 1), TimeDimension.MONTH, new double[]{3100});
        when(tsRepo.readSumSimple(eq(List.of(2L)), eq(TimeDimension.MONTH), eq(start), eq(end)))
                .thenReturn(monthSlice);

        AggregationService.AggregationResult result = service.aggregate(List.of(1L, 2L), start, end, AggregationFunction.SUM);

        assertThat(result.targetDimension()).isEqualTo(TimeDimension.DAY);
        assertThat(result.sumSlice().getValues().length).isGreaterThan(0);
    }

    // ---- Helpers ----

    private static LocalDateTime dt(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0);
    }

    private static TimeSeriesHeader header(long tsId, String key, TimeDimension dim, Unit unit) {
        TimeSeriesHeader h = new TimeSeriesHeader(key, dim, unit);
        h.setTsId(tsId);
        return h;
    }
}
