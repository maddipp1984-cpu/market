package de.market.timeseries.rest;

import de.market.timeseries.api.AggregationService;
import de.market.timeseries.api.TimeSeriesService;
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
import de.market.timeseries.rest.dto.WriteSimpleValueRequest;
import de.market.timeseries.rest.dto.WriteValuesRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timeseries")
public class TimeSeriesController {

    private final TimeSeriesService service;
    private final AggregationService aggregationService;

    public TimeSeriesController(TimeSeriesService service, AggregationService aggregationService) {
        this.service = service;
        this.aggregationService = aggregationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateTimeSeriesRequest req) {
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
    public ResponseEntity<TimeSeriesHeaderResponse> getById(@PathVariable long tsId) {
        return service.getHeader(tsId)
                .map(h -> ResponseEntity.ok(TimeSeriesHeaderResponse.from(h)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(params = "key")
    public ResponseEntity<TimeSeriesHeaderResponse> getByKey(@RequestParam String key) {
        return service.getHeader(key)
                .map(h -> ResponseEntity.ok(TimeSeriesHeaderResponse.from(h)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{tsId}/values")
    public ResponseEntity<Void> writeDay(@PathVariable long tsId,
                                         @Valid @RequestBody WriteValuesRequest req)  {
        service.writeDay(tsId, req.getDate(), req.getValues());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tsId}/values")
    public ResponseEntity<TimeSeriesValuesResponse> read(@PathVariable long tsId,
                                                          @RequestParam LocalDateTime start,
                                                          @RequestParam LocalDateTime end)  {
        TimeSeriesSlice slice = service.read(tsId, start, end);
        return ResponseEntity.ok(TimeSeriesValuesResponse.from(slice));
    }

    @PostMapping("/{tsId}/value")
    public ResponseEntity<Void> writeSimple(@PathVariable long tsId,
                                             @Valid @RequestBody WriteSimpleValueRequest req)  {
        service.writeSimple(tsId, req.getDate(), req.getValue());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tsId}")
    public ResponseEntity<Void> delete(@PathVariable long tsId)  {
        service.deleteTimeSeries(tsId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/aggregate")
    public ResponseEntity<AggregateResponse> aggregate(@RequestBody AggregateRequest req)  {
        AggregationService.AggregationResult result =
                aggregationService.aggregate(req.getTsIds(), req.getStart(), req.getEnd());

        String keys = result.headers().stream()
                .map(TimeSeriesHeader::getTsKey).collect(Collectors.joining(", "));

        TimeSeriesHeaderResponse headerResp = new TimeSeriesHeaderResponse();
        headerResp.setSynthetic(-1, "SUM(" + keys + ")", result.targetDimension().name(),
                result.targetUnit().getSymbol(), null, null,
                "Summierung von " + result.headers().size() + " Zeitreihen");

        TimeSeriesValuesResponse valuesResp = TimeSeriesValuesResponse.from(result.sumSlice());
        return ResponseEntity.ok(new AggregateResponse(headerResp, valuesResp));
    }

    @DeleteMapping("/{tsId}/values")
    public ResponseEntity<Map<String, Integer>> deleteValues(@PathVariable long tsId)  {
        int deleted = service.deleteValues(tsId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/{tsId}/count")
    public ResponseEntity<Map<String, Long>> count(@PathVariable long tsId)  {
        long count = service.count(tsId);
        return ResponseEntity.ok(Map.of("count", count));
    }

}
