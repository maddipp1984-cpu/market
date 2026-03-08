package de.market.scheduling.rest;

import de.market.scheduling.rest.dto.BatchScheduleDto;
import de.market.scheduling.rest.dto.JobCatalogDto;
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
public class SchedulingController {

    private static final List<ColumnMeta> SCHEDULE_COLUMNS = List.of(
            new ColumnMeta("id", "ID", "id", "NUMBER"),
            new ColumnMeta("jobKey", "Job-Key", "job_key", "TEXT"),
            new ColumnMeta("name", "Planungsname", "name", "TEXT"),
            new ColumnMeta("scheduleType", "Schedule-Typ", "schedule_type", "TEXT"),
            new ColumnMeta("enabled", "Aktiv", "enabled", "TEXT"),
            new ColumnMeta("cronExpression", "Cron", "cron_expression", "TEXT"),
            new ColumnMeta("intervalSeconds", "Intervall (s)", "interval_seconds", "NUMBER"),
            new ColumnMeta("lastRun", "Letzte Ausfuehrung", "last_run", "TEXT"),
            new ColumnMeta("lastStatus", "Letzter Status", "last_status", "TEXT")
    );

    private static final List<ColumnMeta> HISTORY_COLUMNS = List.of(
            new ColumnMeta("id", "ID", "id", "NUMBER"),
            new ColumnMeta("scheduleId", "Schedule-ID", "schedule_id", "NUMBER"),
            new ColumnMeta("jobKey", "Job-Key", "job_key", "TEXT"),
            new ColumnMeta("scheduleName", "Planung", "schedule_name", "TEXT"),
            new ColumnMeta("startTime", "Gestartet", "start_time", "TEXT"),
            new ColumnMeta("endTime", "Beendet", "end_time", "TEXT"),
            new ColumnMeta("duration", "Dauer", "duration", "NUMBER"),
            new ColumnMeta("status", "Status", "status", "TEXT"),
            new ColumnMeta("triggeredBy", "Ausgeloest durch", "triggered_by", "TEXT"),
            new ColumnMeta("recordsAffected", "Ergebnis", "records_affected", "NUMBER")
    );

    private final SchedulingService service;

    public SchedulingController(SchedulingService service) {
        this.service = service;
    }

    // --- Job-Katalog ---

    @GetMapping("/api/batch-jobs/catalog")
    public ResponseEntity<List<JobCatalogDto>> getCatalog() {
        return ResponseEntity.ok(service.getJobCatalog());
    }

    // --- Schedules ---

    @GetMapping("/api/batch-schedules")
    public ResponseEntity<TableResponse> getAllSchedules() {
        List<Map<String, Object>> data = service.findAllSchedulesAsRows();
        return ResponseEntity.ok(new TableResponse(SCHEDULE_COLUMNS, data));
    }

    @GetMapping("/api/batch-schedules/{id}")
    public ResponseEntity<BatchScheduleDto> getScheduleById(@PathVariable int id) {
        return ResponseEntity.ok(service.findScheduleById(id));
    }

    @PostMapping("/api/batch-schedules")
    public ResponseEntity<BatchScheduleDto> createSchedule(@RequestBody BatchScheduleDto dto) {
        return ResponseEntity.ok(service.createSchedule(dto));
    }

    @PutMapping("/api/batch-schedules/{id}")
    public ResponseEntity<BatchScheduleDto> updateSchedule(@PathVariable int id, @RequestBody BatchScheduleDto dto) {
        return ResponseEntity.ok(service.updateSchedule(id, dto));
    }

    @DeleteMapping("/api/batch-schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable int id) {
        service.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/batch-schedules/{id}/trigger")
    public ResponseEntity<Void> triggerSchedule(@PathVariable int id,
                                                 @RequestBody(required = false) Map<String, Object> adhocParameters) {
        service.triggerManually(id, adhocParameters);
        return ResponseEntity.accepted().build();
    }

    // --- Historie ---

    @GetMapping("/api/batch-history")
    public ResponseEntity<TableResponse> getFullHistory(
            @RequestParam(defaultValue = "100") int limit) throws SQLException {
        List<Map<String, Object>> data = service.getFullHistory(limit);
        return ResponseEntity.ok(new TableResponse(HISTORY_COLUMNS, data));
    }

    @GetMapping("/api/batch-schedules/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> getScheduleHistory(
            @PathVariable int id,
            @RequestParam(defaultValue = "50") int limit) throws SQLException {
        return ResponseEntity.ok(service.getHistory(id, limit));
    }

    @GetMapping(value = "/api/batch-history/{execId}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLogByExecutionId(@PathVariable long execId) {
        return ResponseEntity.ok(service.getLogContentByExecutionId(execId));
    }
}
