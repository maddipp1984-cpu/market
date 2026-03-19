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

import de.market.timeseries.client.DimensionConverter;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
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

        // 6. Gruppiert: Pro Dimension per SQL summieren, dann disaggregieren + zusammenfuehren
        //    Statt N parallele Reads nur max 5 SQL-Queries (eine pro Dimension)
        Map<TimeDimension, List<Long>> idsByDim = new LinkedHashMap<>();
        for (TimeSeriesHeader h : headers) {
            idsByDim.computeIfAbsent(h.getTimeDimension(), k -> new ArrayList<>()).add(h.getTsId());
        }

        long t2 = System.currentTimeMillis();
        log.info("Aggregation gruppiert: {} Dimensionen, {} Zeitreihen",
                idsByDim.size(), headers.size());

        // Pro Dimension parallel per SQL summieren
        List<CompletableFuture<TimeSeriesSlice>> groupFutures = new ArrayList<>();
        for (Map.Entry<TimeDimension, List<Long>> entry : idsByDim.entrySet()) {
            TimeDimension dim = entry.getKey();
            List<Long> ids = entry.getValue();
            groupFutures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    TimeSeriesSlice groupSum;
                    if (dim.useTimestamptz()) {
                        groupSum = tsRepo.readSumSubdaily(ids, dim, start, end);
                    } else {
                        groupSum = tsRepo.readSumSimple(ids, dim, start, end);
                    }
                    log.info("  Gruppe {}: {} ZR summiert, {} Werte",
                            dim, ids.size(), groupSum.size());

                    // Disaggregieren auf Zieldimension falls noetig
                    if (dim != targetDim) {
                        groupSum = DimensionConverter.disaggregate(groupSum, targetDim, AggregationFunction.SUM);
                    }
                    return groupSum;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, aggregationExecutor));
        }

        // Auf alle Gruppen-Ergebnisse warten
        List<TimeSeriesSlice> groupSlices;
        try {
            groupSlices = groupFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException) throw (SQLException) cause;
            throw new RuntimeException(e);
        }

        // 7. Gruppen-Ergebnisse elementweise summieren
        int maxLen = groupSlices.stream().mapToInt(s -> s.getValues().length).max().orElse(0);
        double[] sumValues = new double[maxLen];

        for (TimeSeriesSlice slice : groupSlices) {
            double[] vals = slice.getValues();
            for (int j = 0; j < vals.length; j++) {
                if (!Double.isNaN(vals[j])) {
                    sumValues[j] += vals[j];
                }
            }
        }

        long t3 = System.currentTimeMillis();
        log.info("Aggregation gruppiert fertig: {} Werte in {} ms (gesamt: {} ms)",
                maxLen, t3 - t2, t3 - t0);

        TimeSeriesSlice resultSlice = groupSlices.get(0);
        TimeSeriesSlice finalSlice = new TimeSeriesSlice(
                resultSlice.getStart(), resultSlice.getEnd(), targetDim, sumValues);

        return new AggregationResult(headers, targetDim, targetUnit, finalSlice);
    }
}
