package de.market.scheduling.rest;

import de.market.scheduling.rest.dto.BatchJobDto;
import de.market.scheduling.service.SchedulingService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.TableResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch-jobs")
public class SchedulingController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "id", "NUMBER"),
            new ColumnMeta("jobKey", "Job-Key", "job_key", "TEXT"),
            new ColumnMeta("name", "Name", "name", "TEXT"),
            new ColumnMeta("scheduleType", "Schedule", "schedule_type", "TEXT"),
            new ColumnMeta("enabled", "Aktiv", "enabled", "TEXT"),
            new ColumnMeta("lastRun", "Letzte Ausfuehrung", "last_run", "TEXT"),
            new ColumnMeta("lastStatus", "Letzter Status", "last_status", "TEXT")
    );

    private final SchedulingService service;

    public SchedulingController(SchedulingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchJobDto> getById(@PathVariable int id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BatchJobDto> update(@PathVariable int id, @RequestBody BatchJobDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<Void> trigger(@PathVariable int id) {
        service.triggerManually(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @PathVariable int id,
            @RequestParam(defaultValue = "50") int limit) throws SQLException {
        return ResponseEntity.ok(service.getHistory(id, limit));
    }

    @GetMapping(value = "/{id}/history/{execId}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLog(@PathVariable int id, @PathVariable long execId) {
        return ResponseEntity.ok(service.getLogContent(id, execId));
    }
}
