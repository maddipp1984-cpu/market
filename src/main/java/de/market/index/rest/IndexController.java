package de.market.index.rest;

import de.market.index.rest.dto.IndexDto;
import de.market.index.service.IndexService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.JooqFilterBuilder;
import de.market.shared.dto.TableResponse;
import org.jooq.Condition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "index_id", "NUMBER"),
            new ColumnMeta("name", "Name", "object_key", "TEXT"),
            new ColumnMeta("description", "Beschreibung", "description", "TEXT"),
            new ColumnMeta("timeDim", "Zeitdimension", "time_dim", "NUMBER"),
            new ColumnMeta("unit", "Einheit", "symbol", "TEXT"),
            new ColumnMeta("currency", "Waehrung", "iso_code", "TEXT"),
            new ColumnMeta("firstDate", "Erster Wert", "first_date", "DATE"),
            new ColumnMeta("lastDate", "Letzter Wert", "last_date", "DATE"),
            new ColumnMeta("createdAt", "Erstellt", "created_at", "DATE")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final IndexService service;

    public IndexController(IndexService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            Condition condition = JooqFilterBuilder.build(request.getConditions(), ALLOWED_SQL_COLUMNS);
            data = service.findFiltered(condition);
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndexDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<IndexDto> create(@RequestBody IndexDto dto) {
        IndexDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IndexDto> update(@PathVariable Long id, @RequestBody IndexDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
