package de.market.timeseries.rest;

import de.market.timeseries.api.TimeSeriesService;
import de.market.timeseries.client.AggregationFunction;
import de.market.timeseries.client.TimeSeriesClient;
import de.market.timeseries.model.Currency;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;
import de.market.timeseries.rest.dto.AggregateRequest;
import de.market.timeseries.rest.dto.AggregateResponse;
import de.market.timeseries.rest.dto.CreateTimeSeriesRequest;
import de.market.timeseries.rest.dto.TimeSeriesHeaderResponse;
import de.market.timeseries.rest.dto.TimeSeriesValuesResponse;
import de.market.timeseries.rest.dto.WriteValuesRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timeseries")
public class TimeSeriesController {

    private final TimeSeriesService service;
    private final TimeSeriesClient client;

    public TimeSeriesController(TimeSeriesService service, TimeSeriesClient client) {
        this.service = service;
        this.client = client;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateTimeSeriesRequest req) throws SQLException {
        TimeDimension dim = EnumParser.parse(TimeDimension.class, req.getDimension(), "dimension");
        Unit unit = EnumParser.parse(Unit.class, req.getUnit(), "unit");
        Currency currency = req.getCurrency() != null
                ? EnumParser.parse(Currency.class, req.getCurrency(), "currency") : null;

        long tsId;
        if (currency != null) {
            tsId = service.createTimeSeries(req.getKey(), dim, unit, currency, req.getDescription());
        } else {
            tsId = service.createTimeSeries(req.getKey(), dim, unit, req.getDescription());
        }

        return ResponseEntity.status(201).body(Map.of("tsId", tsId));
    }

    @GetMapping("/{tsId}")
    public ResponseEntity<TimeSeriesHeaderResponse> getById(@PathVariable long tsId) throws SQLException {
        return service.getHeader(tsId)
                .map(h -> ResponseEntity.ok(TimeSeriesHeaderResponse.from(h)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(params = "key")
    public ResponseEntity<TimeSeriesHeaderResponse> getByKey(@RequestParam String key) throws SQLException {
        return service.getHeader(key)
                .map(h -> ResponseEntity.ok(TimeSeriesHeaderResponse.from(h)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{tsId}/values")
    public ResponseEntity<Void> writeDay(@PathVariable long tsId,
                                         @Valid @RequestBody WriteValuesRequest req) throws SQLException {
        service.writeDay(tsId, req.getDate(), req.getValues());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tsId}/values")
    public ResponseEntity<TimeSeriesValuesResponse> read(@PathVariable long tsId,
                                                          @RequestParam LocalDateTime start,
                                                          @RequestParam LocalDateTime end) throws SQLException {
        TimeSeriesSlice slice = service.read(tsId, start, end);
        return ResponseEntity.ok(TimeSeriesValuesResponse.from(slice));
    }

    @DeleteMapping("/{tsId}")
    public ResponseEntity<Void> delete(@PathVariable long tsId) throws SQLException {
        service.deleteTimeSeries(tsId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/aggregate")
    public ResponseEntity<AggregateResponse> aggregate(@RequestBody AggregateRequest req) throws SQLException {
        List<Long> tsIds = req.getTsIds();
        if (tsIds == null || tsIds.size() < 2) {
            throw new IllegalArgumentException("Mindestens 2 Zeitreihen erforderlich");
        }

        // 1. Alle Header laden
        List<TimeSeriesHeader> headers = new ArrayList<>();
        for (long id : tsIds) {
            headers.add(service.getHeader(id)
                    .orElseThrow(() -> new IllegalArgumentException("Zeitreihe nicht gefunden: tsId=" + id)));
        }

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

        // 5. Alle Zeitreihen lesen, konvertieren, summieren
        double[] sumValues = null;
        TimeSeriesSlice resultSlice = null;

        for (int i = 0; i < tsIds.size(); i++) {
            TimeSeriesHeader h = headers.get(i);
            Unit sourceUnit = h.getUnit();
            boolean needsDimConvert = h.getTimeDimension() != targetDim;
            boolean needsUnitConvert = sourceUnit != targetUnit;

            TimeSeriesSlice slice;
            if (needsDimConvert || needsUnitConvert) {
                slice = client.read(tsIds.get(i), req.getStart(), req.getEnd(),
                        targetDim, AggregationFunction.SUM,
                        needsUnitConvert ? targetUnit : null);
            } else {
                slice = client.read(tsIds.get(i), req.getStart(), req.getEnd());
            }

            if (sumValues == null) {
                sumValues = slice.getValues().clone();
                resultSlice = slice;
            } else {
                double[] vals = slice.getValues();
                int len = Math.min(sumValues.length, vals.length);
                for (int j = 0; j < len; j++) {
                    double a = Double.isNaN(sumValues[j]) ? 0 : sumValues[j];
                    double b = Double.isNaN(vals[j]) ? 0 : vals[j];
                    sumValues[j] = a + b;
                }
            }
        }

        // 6. Synthetischen Header + Response bauen
        String keys = headers.stream().map(TimeSeriesHeader::getTsKey).collect(Collectors.joining(", "));
        TimeSeriesSlice finalSlice = new TimeSeriesSlice(
                resultSlice.getStart(), resultSlice.getEnd(), targetDim, sumValues);

        TimeSeriesHeaderResponse headerResp = new TimeSeriesHeaderResponse();
        headerResp.setSynthetic(-1, "SUM(" + keys + ")", targetDim.name(),
                targetUnit.getSymbol(), null, null,
                "Summierung von " + headers.size() + " Zeitreihen");

        TimeSeriesValuesResponse valuesResp = TimeSeriesValuesResponse.from(finalSlice);

        return ResponseEntity.ok(new AggregateResponse(headerResp, valuesResp));
    }

    @DeleteMapping("/{tsId}/values")
    public ResponseEntity<Map<String, Integer>> deleteValues(@PathVariable long tsId) throws SQLException {
        int deleted = service.deleteValues(tsId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/{tsId}/count")
    public ResponseEntity<Map<String, Long>> count(@PathVariable long tsId) throws SQLException {
        long count = service.count(tsId);
        return ResponseEntity.ok(Map.of("count", count));
    }

}
