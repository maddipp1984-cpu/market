package de.market.timeseries.api;

import de.market.timeseries.client.AggregationFunction;
import de.market.timeseries.client.TimeSeriesClient;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;
import de.market.timeseries.repository.TimeSeriesRepository;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    private final ExecutorService aggregationExecutor = Executors.newFixedThreadPool(10);

    private final TimeSeriesClient client;
    private final TimeSeriesRepository tsRepo;
    private final de.market.timeseries.repository.HeaderRepository headerRepo;

    public AggregationService(TimeSeriesClient client,
                              TimeSeriesRepository tsRepo,
                              de.market.timeseries.repository.HeaderRepository headerRepo) {
        this.client = client;
        this.tsRepo = tsRepo;
        this.headerRepo = headerRepo;
    }

    @PreDestroy
    public void shutdown() {
        aggregationExecutor.shutdown();
        try {
            if (!aggregationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                aggregationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aggregationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record AggregationResult(
            List<TimeSeriesHeader> headers,
            TimeDimension targetDimension,
            Unit targetUnit,
            TimeSeriesSlice sumSlice
    ) {}

    public AggregationResult aggregate(List<Long> tsIds, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        if (tsIds == null || tsIds.size() < 2) {
            throw new IllegalArgumentException("Mindestens 2 Zeitreihen erforderlich");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end muss nach start liegen: " + start + " / " + end);
        }

        long t0 = System.currentTimeMillis();

        // 1. Alle Header in einer Query laden
        List<TimeSeriesHeader> headers = headerRepo.findByIds(tsIds);
        if (headers.size() != tsIds.size()) {
            throw new IllegalArgumentException("Nicht alle Zeitreihen gefunden: erwartet "
                    + tsIds.size() + ", gefunden " + headers.size());
        }

        long t1 = System.currentTimeMillis();
        log.info("Aggregation: {} Header geladen in {} ms", headers.size(), t1 - t0);

        // 2. Kleinste Dimension bestimmen
        TimeDimension targetDim = headers.stream()
                .map(TimeSeriesHeader::getTimeDimension)
                .min((a, b) -> Integer.compare(a.getCode(), b.getCode()))
                .orElseThrow();

        // 3. Ziel-Einheit = erste Zeitreihe
        Unit targetUnit = headers.get(0).getUnit();

        // 4. Kompatibilitaet pruefen
        for (TimeSeriesHeader h : headers) {
            Unit u = h.getUnit();
            if (u != targetUnit && !u.isConvertibleTo(targetUnit) && !u.isCrossDomainConvertibleTo(targetUnit)) {
                throw new IllegalArgumentException(
                        "Einheit " + u.getSymbol() + " (" + h.getTsKey()
                        + ") nicht konvertierbar zu " + targetUnit.getSymbol());
            }
        }

        // 5. SQL-Shortcut: Gleiche Dimension + Einheit → Summierung komplett in PostgreSQL
        boolean allSameDim = headers.stream().allMatch(h -> h.getTimeDimension() == targetDim);
        boolean allSameUnit = headers.stream().allMatch(h -> h.getUnit() == targetUnit);

        if (allSameDim && allSameUnit) {
            long t2 = System.currentTimeMillis();
            TimeSeriesSlice sumSlice;
            if (targetDim.useTimestamptz()) {
                sumSlice = tsRepo.readSumSubdaily(tsIds, targetDim, start, end);
            } else {
                sumSlice = tsRepo.readSumSimple(tsIds, targetDim, start, end);
            }
            long t3 = System.currentTimeMillis();
            log.info("Aggregation SQL-Shortcut: {} Werte in {} ms (gesamt: {} ms)",
                    sumSlice.size(), t3 - t2, t3 - t0);
            return new AggregationResult(headers, targetDim, targetUnit, sumSlice);
        }

        // 6. Parallele Reads mit dediziertem Executor
        List<CompletableFuture<TimeSeriesSlice>> futures = new ArrayList<>();
        for (int i = 0; i < tsIds.size(); i++) {
            final TimeSeriesHeader h = headers.get(i);
            final long id = tsIds.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Unit sourceUnit = h.getUnit();
                    boolean needsDimConvert = h.getTimeDimension() != targetDim;
                    boolean needsUnitConvert = sourceUnit != targetUnit;
                    if (needsDimConvert || needsUnitConvert) {
                        return client.read(id, start, end,
                                targetDim, AggregationFunction.SUM,
                                needsUnitConvert ? targetUnit : null);
                    } else {
                        return client.read(id, start, end);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, aggregationExecutor));
        }

        // Auf alle Ergebnisse warten
        List<TimeSeriesSlice> slices;
        try {
            slices = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException) throw (SQLException) cause;
            throw new RuntimeException(e);
        }

        // 7. Summieren mit korrekter Array-Erweiterung
        int maxLen = slices.stream().mapToInt(s -> s.getValues().length).max().orElse(0);
        double[] sumValues = new double[maxLen];
        Arrays.fill(sumValues, 0);

        for (TimeSeriesSlice slice : slices) {
            double[] vals = slice.getValues();
            for (int j = 0; j < vals.length; j++) {
                if (!Double.isNaN(vals[j])) {
                    sumValues[j] += vals[j];
                }
            }
        }

        TimeSeriesSlice resultSlice = slices.get(0);
        TimeSeriesSlice finalSlice = new TimeSeriesSlice(
                resultSlice.getStart(), resultSlice.getEnd(), targetDim, sumValues);

        return new AggregationResult(headers, targetDim, targetUnit, finalSlice);
    }
}
