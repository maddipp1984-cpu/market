package de.market.timeseries.rest;

import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterQueryBuilder;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.TableResponse;
import de.market.timeseries.repository.TimeSeriesOverviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timeseries-overview")
public class TimeSeriesOverviewController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "h.ts_id", "NUMBER"),
            new ColumnMeta("key", "Schluessel", "h.ts_key", "TEXT"),
            new ColumnMeta("dimension", "Dimension", "CASE h.time_dim WHEN 1 THEN '15 Minuten' WHEN 2 THEN '1 Stunde' WHEN 3 THEN 'Tag' WHEN 4 THEN 'Monat' WHEN 5 THEN 'Jahr' END", "TEXT"),
            new ColumnMeta("unit", "Einheit", "u.symbol", "TEXT"),
            new ColumnMeta("currency", "Waehrung", "c.iso_code", "TEXT"),
            new ColumnMeta("object", "Objekt", "o.object_key", "TEXT"),
            new ColumnMeta("description", "Beschreibung", "h.description", "TEXT"),
            new ColumnMeta("createdAt", "Erstellt", "h.created_at", "TIMESTAMP"),
            new ColumnMeta("updatedAt", "Geaendert", "h.updated_at", "TIMESTAMP")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final TimeSeriesOverviewRepository repository;

    public TimeSeriesOverviewController(TimeSeriesOverviewRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = repository.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) throws SQLException {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            FilterQueryBuilder.WhereClause where = FilterQueryBuilder.build(
                    request.getConditions(), ALLOWED_SQL_COLUMNS);
            data = repository.findFiltered(where.getSql(), where.getParams());
        } else {
            data = repository.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }
}
