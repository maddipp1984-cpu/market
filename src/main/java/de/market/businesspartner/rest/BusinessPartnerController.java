package de.market.businesspartner.rest;

import de.market.businesspartner.rest.dto.BusinessPartnerDto;
import de.market.businesspartner.service.BusinessPartnerService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.TableResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/business-partners")
public class BusinessPartnerController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "bp.id", "NUMBER"),
            new ColumnMeta("shortName", "Kurzbezeichnung", "bp.short_name", "TEXT"),
            new ColumnMeta("name", "Name", "bp.name", "TEXT")
    );

    private final BusinessPartnerService service;

    public BusinessPartnerController(BusinessPartnerService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessPartnerDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<BusinessPartnerDto> create(@RequestBody BusinessPartnerDto dto) {
        BusinessPartnerDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessPartnerDto> update(@PathVariable Long id, @RequestBody BusinessPartnerDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
