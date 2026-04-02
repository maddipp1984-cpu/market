package de.market.commoditygroup.rest;

import de.market.commoditygroup.rest.dto.CommodityGroupDto;
import de.market.commoditygroup.service.CommodityGroupService;
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
@RequestMapping("/api/commodity-groups")
public class CommodityGroupController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "commodity_group_id", "NUMBER"),
            new ColumnMeta("name", "Name", "name", "TEXT")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final CommodityGroupService service;

    public CommodityGroupController(CommodityGroupService service) {
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
    public ResponseEntity<CommodityGroupDto> getById(@PathVariable Short id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<CommodityGroupDto> create(@RequestBody CommodityGroupDto dto) {
        CommodityGroupDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommodityGroupDto> update(@PathVariable Short id, @RequestBody CommodityGroupDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
