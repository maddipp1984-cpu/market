package de.market.shared.rest;

import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.JooqFilterBuilder;
import de.market.shared.dto.TableResponse;
import de.market.shared.service.AbstractCrudService;
import org.jooq.Condition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generischer Basis-Controller fuer Stammdaten-CRUD-Module.
 * Subklassen muessen nur noch die COLUMNS-Liste und den Service bereitstellen.
 *
 * @param <D> DTO-Typ
 * @param <ID> ID-Typ (Short, Long, ...)
 */
public abstract class AbstractCrudController<D, ID> {

    private final List<ColumnMeta> columns;
    private final Set<String> allowedSqlColumns;
    private final AbstractCrudService<D, ?, ID> service;

    protected AbstractCrudController(AbstractCrudService<D, ?, ID> service, List<ColumnMeta> columns) {
        this.service = service;
        this.columns = columns;
        this.allowedSqlColumns = columns.stream()
                .map(ColumnMeta::getSqlColumn)
                .collect(Collectors.toSet());
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(columns, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            Condition condition = JooqFilterBuilder.build(request.getConditions(), allowedSqlColumns);
            data = service.findFiltered(condition);
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(columns, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<D> getById(@PathVariable ID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<D> create(@RequestBody D dto) {
        D created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<D> update(@PathVariable ID id, @RequestBody D dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable ID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
