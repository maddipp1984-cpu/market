package de.market.currency.rest;

import de.market.currency.rest.dto.CurrencyDto;
import de.market.currency.service.CurrencyService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterQueryBuilder;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.TableResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "currency_id", "NUMBER"),
            new ColumnMeta("isoCode", "ISO-Code", "iso_code", "TEXT"),
            new ColumnMeta("description", "Name", "description", "TEXT")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) throws SQLException {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            FilterQueryBuilder.WhereClause where = FilterQueryBuilder.build(
                    request.getConditions(), ALLOWED_SQL_COLUMNS);
            data = service.findFiltered(where.getSql(), where.getParams());
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurrencyDto> getById(@PathVariable Short id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<CurrencyDto> create(@RequestBody CurrencyDto dto) {
        CurrencyDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurrencyDto> update(@PathVariable Short id, @RequestBody CurrencyDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
