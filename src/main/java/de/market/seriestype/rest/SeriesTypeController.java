package de.market.seriestype.rest;

import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.seriestype.service.SeriesTypeService;
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
@RequestMapping("/api/series-types")
public class SeriesTypeController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "series_type_id", "NUMBER"),
            new ColumnMeta("code", "Kuerzel", "code", "TEXT"),
            new ColumnMeta("name", "Name", "name", "TEXT"),
            new ColumnMeta("category", "Kategorie", "category", "TEXT")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final SeriesTypeService service;

    public SeriesTypeController(SeriesTypeService service) {
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
    public ResponseEntity<SeriesTypeDto> getById(@PathVariable Short id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<SeriesTypeDto> create(@RequestBody SeriesTypeDto dto) {
        SeriesTypeDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeriesTypeDto> update(@PathVariable Short id, @RequestBody SeriesTypeDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
