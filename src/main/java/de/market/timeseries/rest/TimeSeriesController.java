package de.market.timeseries.rest;

import de.market.timeseries.api.TimeSeriesService;
import de.market.timeseries.model.Currency;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import de.market.timeseries.model.Unit;
import de.market.timeseries.rest.dto.CreateTimeSeriesRequest;
import de.market.timeseries.rest.dto.TimeSeriesHeaderResponse;
import de.market.timeseries.rest.dto.TimeSeriesValuesResponse;
import de.market.timeseries.rest.dto.WriteValuesRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/timeseries")
public class TimeSeriesController {

    private final TimeSeriesService service;

    public TimeSeriesController(TimeSeriesService service) {
        this.service = service;
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
