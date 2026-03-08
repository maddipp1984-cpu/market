package de.market.scheduling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.market.scheduling.jobs.AbstractBatchJob;
import de.market.scheduling.jobs.QuartzJobAdapter;
import de.market.scheduling.model.BatchScheduleEntity;
import de.market.scheduling.model.JobParameter;
import de.market.scheduling.model.ScheduleType;
import de.market.scheduling.repository.BatchScheduleJpaRepository;
import de.market.scheduling.repository.JobExecutionLogRepository;
import de.market.scheduling.repository.ScheduleOverviewRepository;
import de.market.scheduling.rest.dto.BatchScheduleDto;
import de.market.scheduling.rest.dto.JobCatalogDto;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final BatchScheduleJpaRepository scheduleRepository;
    private final ScheduleOverviewRepository overviewRepository;
    private final JobExecutionLogRepository executionLogRepository;
    private final Scheduler scheduler;
    private final List<AbstractBatchJob> batchJobs;
    private final ObjectMapper objectMapper;

    public SchedulingService(BatchScheduleJpaRepository scheduleRepository,
                              ScheduleOverviewRepository overviewRepository,
                              JobExecutionLogRepository executionLogRepository,
                              Scheduler scheduler,
                              List<AbstractBatchJob> batchJobs,
                              ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.overviewRepository = overviewRepository;
        this.executionLogRepository = executionLogRepository;
        this.scheduler = scheduler;
        this.batchJobs = batchJobs;
        this.objectMapper = objectMapper;
    }

    // --- Job-Katalog ---

    @Transactional(readOnly = true)
    public List<JobCatalogDto> getJobCatalog() {
        return batchJobs.stream().map(job -> {
            JobCatalogDto dto = new JobCatalogDto();
            dto.setJobKey(job.getJobKey());
            dto.setName(job.getName());
            dto.setDescription(job.getDescription());
            dto.setParameters(job.getParameters().stream()
                    .map(JobCatalogDto.ParameterDto::from)
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    // --- Schedule-Uebersicht ---

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllSchedulesAsRows() {
        return overviewRepository.findAllAsRows();
    }

    // --- Schedule CRUD ---

    @Transactional(readOnly = true)
    public BatchScheduleDto findScheduleById(int id) {
        BatchScheduleEntity entity = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public BatchScheduleDto createSchedule(BatchScheduleDto dto) {
        // Validate jobKey exists in catalog
        AbstractBatchJob job = findJobByKey(dto.getJobKey());
        if (job == null) {
            throw new IllegalArgumentException("Unbekannter Job-Key: " + dto.getJobKey());
        }

        // Validate parameters against catalog
        if (dto.getParameters() != null && !dto.getParameters().isEmpty()) {
            validateParameters(job, dto.getParameters());
        }

        ScheduleType scheduleType = parseScheduleType(dto.getScheduleType());
        validateScheduleConfig(scheduleType, dto.getCronExpression(), dto.getIntervalSeconds());

        BatchScheduleEntity entity = new BatchScheduleEntity();
        entity.setJobKey(dto.getJobKey());
        entity.setName(dto.getName());
        entity.setScheduleType(scheduleType);
        entity.setCronExpression(dto.getCronExpression());
        entity.setIntervalSeconds(dto.getIntervalSeconds());
        entity.setEnabled(dto.isEnabled());
        entity.setParameters(toJson(dto.getParameters()));

        entity = scheduleRepository.save(entity);

        updateQuartzTrigger(entity);

        return toDto(entity);
    }

    public BatchScheduleDto updateSchedule(int id, BatchScheduleDto dto) {
        BatchScheduleEntity entity = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule nicht gefunden: id=" + id));

        // Validate jobKey if changed
        if (dto.getJobKey() != null && !dto.getJobKey().equals(entity.getJobKey())) {
            AbstractBatchJob job = findJobByKey(dto.getJobKey());
            if (job == null) {
                throw new IllegalArgumentException("Unbekannter Job-Key: " + dto.getJobKey());
            }
            entity.setJobKey(dto.getJobKey());
        }

        // Validate parameters
        if (dto.getParameters() != null) {
            AbstractBatchJob job = findJobByKey(entity.getJobKey());
            if (job != null && !dto.getParameters().isEmpty()) {
                validateParameters(job, dto.getParameters());
            }
            entity.setParameters(toJson(dto.getParameters()));
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        ScheduleType scheduleType = dto.getScheduleType() != null
                ? parseScheduleType(dto.getScheduleType())
                : entity.getScheduleType();
        validateScheduleConfig(scheduleType,
                dto.getCronExpression() != null ? dto.getCronExpression() : entity.getCronExpression(),
                dto.getIntervalSeconds() != null ? dto.getIntervalSeconds() : entity.getIntervalSeconds());

        entity.setScheduleType(scheduleType);
        entity.setEnabled(dto.isEnabled());
        if (dto.getCronExpression() != null) {
            entity.setCronExpression(dto.getCronExpression());
        }
        if (dto.getIntervalSeconds() != null) {
            entity.setIntervalSeconds(dto.getIntervalSeconds());
        }

        entity = scheduleRepository.save(entity);

        updateQuartzTrigger(entity);

        return toDto(entity);
    }

    public void deleteSchedule(int id) {
        BatchScheduleEntity entity = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule nicht gefunden: id=" + id));

        // Remove Quartz trigger
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey("schedule-" + id, "batch-schedules");
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            JobKey jobKey = JobKey.jobKey("schedule-" + id, "batch-schedules");
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            log.error("Fehler beim Entfernen des Quartz-Triggers fuer Schedule {}", id, e);
        }

        scheduleRepository.delete(entity);
        log.info("Schedule geloescht: id={}, name={}", id, entity.getName());
    }

    // --- Manuelles Ausloesen ---

    public void triggerManually(int id, Map<String, Object> adhocParameters) {
        BatchScheduleEntity entity = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule nicht gefunden: id=" + id));

        // Merge: saved parameters + adhoc overrides
        Map<String, Object> mergedParams = fromJson(entity.getParameters());
        if (adhocParameters != null && !adhocParameters.isEmpty()) {
            mergedParams.putAll(adhocParameters);
        }

        try {
            JobKey jobKey = JobKey.jobKey("schedule-" + id, "batch-schedules");

            // Ensure the job is registered in Quartz
            if (!scheduler.checkExists(jobKey)) {
                JobDetail jobDetail = buildJobDetail(entity);
                scheduler.addJob(jobDetail, true);
            }

            // Trigger with MANUAL flag and parameters
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("scheduleId", entity.getId());
            dataMap.put("triggeredBy", "MANUAL");
            dataMap.put("parameters", toJson(mergedParams));
            scheduler.triggerJob(jobKey, dataMap);

            log.info("Schedule {} manuell ausgeloest (jobKey={})", id, entity.getJobKey());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Konnte Schedule nicht ausloesen: " + id, e);
        }
    }

    // --- Historie ---

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(int scheduleId, int limit) throws SQLException {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("Schedule nicht gefunden: id=" + scheduleId);
        }
        int effectiveLimit = Math.max(1, Math.min(limit, 200));
        return executionLogRepository.findByScheduleId(scheduleId, effectiveLimit);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullHistory(int limit) throws SQLException {
        int effectiveLimit = Math.max(1, Math.min(limit, 500));
        return executionLogRepository.findAllAsRows(effectiveLimit);
    }

    public String getLogContent(int scheduleId, long executionId) {
        BatchScheduleEntity entity = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule nicht gefunden: id=" + scheduleId));
        String fileName = entity.getJobKey() + "_" + executionId + ".log";
        try {
            java.io.File logDir = new java.io.File("logs/jobs").getCanonicalFile();
            java.io.File logFile = new java.io.File(logDir, fileName).getCanonicalFile();
            if (!logFile.toPath().startsWith(logDir.toPath())) {
                throw new IllegalArgumentException("Ungueltiger Log-Pfad");
            }
            if (!logFile.exists()) {
                return "Logfile nicht gefunden: " + fileName;
            }
            return java.nio.file.Files.readString(logFile.toPath());
        } catch (java.io.IOException e) {
            return "Fehler beim Lesen: " + e.getMessage();
        }
    }

    public String getLogContentByExecutionId(long executionId) {
        String logFilePath;
        try {
            logFilePath = executionLogRepository.findLogFileByExecutionId(executionId);
        } catch (java.sql.SQLException e) {
            return "Fehler beim DB-Zugriff: " + e.getMessage();
        }
        if (logFilePath == null) {
            return "Logfile nicht gefunden fuer executionId=" + executionId;
        }
        try {
            java.io.File logDir = new java.io.File("logs/jobs").getCanonicalFile();
            java.io.File logFile = new java.io.File(logFilePath).getCanonicalFile();
            if (!logFile.toPath().startsWith(logDir.toPath())) {
                throw new IllegalArgumentException("Ungueltiger Log-Pfad");
            }
            if (!logFile.exists()) {
                return "Logfile nicht gefunden: " + logFilePath;
            }
            return java.nio.file.Files.readString(logFile.toPath());
        } catch (java.io.IOException e) {
            return "Fehler beim Lesen: " + e.getMessage();
        }
    }

    // --- Quartz Trigger Management ---

    public void updateQuartzTrigger(BatchScheduleEntity entity) {
        try {
            JobKey jobKey = JobKey.jobKey("schedule-" + entity.getId(), "batch-schedules");
            TriggerKey triggerKey = TriggerKey.triggerKey("schedule-" + entity.getId(), "batch-schedules");

            if (!entity.isEnabled() || entity.getScheduleType() == ScheduleType.NONE) {
                // Remove trigger if exists
                if (scheduler.checkExists(triggerKey)) {
                    scheduler.unscheduleJob(triggerKey);
                    log.info("Trigger entfernt fuer Schedule {} ({})", entity.getId(), entity.getName());
                }
                // Ensure job is still registered (for manual triggering)
                if (!scheduler.checkExists(jobKey)) {
                    scheduler.addJob(buildJobDetail(entity), true);
                }
                return;
            }

            // Build job detail
            JobDetail jobDetail = buildJobDetail(entity);
            scheduler.addJob(jobDetail, true);

            // Build trigger
            Trigger trigger = buildTrigger(entity, triggerKey, jobKey);
            if (trigger == null) return;

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
                log.info("Trigger aktualisiert fuer Schedule {} ({}, {})",
                        entity.getId(), entity.getName(), entity.getScheduleType());
            } else {
                scheduler.scheduleJob(trigger);
                log.info("Trigger erstellt fuer Schedule {} ({}, {})",
                        entity.getId(), entity.getName(), entity.getScheduleType());
            }
        } catch (SchedulerException e) {
            log.error("Fehler beim Aktualisieren des Triggers fuer Schedule {}", entity.getId(), e);
        }
    }

    private JobDetail buildJobDetail(BatchScheduleEntity entity) {
        return JobBuilder.newJob(QuartzJobAdapter.class)
                .withIdentity("schedule-" + entity.getId(), "batch-schedules")
                .usingJobData("scheduleId", entity.getId())
                .usingJobData("triggeredBy", "SCHEDULER")
                .usingJobData("parameters", entity.getParameters() != null ? entity.getParameters() : "{}")
                .storeDurably(true)
                .build();
    }

    private Trigger buildTrigger(BatchScheduleEntity entity, TriggerKey triggerKey, JobKey jobKey) {
        if (entity.getScheduleType() == ScheduleType.CRON) {
            try {
                return TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .forJob(jobKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(entity.getCronExpression()))
                        .build();
            } catch (Exception e) {
                log.error("Ungueltige Cron-Expression fuer Schedule {}: {}", entity.getId(), entity.getCronExpression());
                return null;
            }
        } else if (entity.getScheduleType() == ScheduleType.INTERVAL) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds(entity.getIntervalSeconds())
                            .repeatForever())
                    .build();
        }
        return null;
    }

    // --- Validierung ---

    private void validateScheduleConfig(ScheduleType type, String cronExpression, Integer intervalSeconds) {
        if (type == ScheduleType.CRON && (cronExpression == null || cronExpression.isBlank())) {
            throw new IllegalArgumentException("Cron-Expression ist Pflichtfeld bei Schedule-Typ CRON");
        }
        if (type == ScheduleType.INTERVAL && (intervalSeconds == null || intervalSeconds <= 0)) {
            throw new IllegalArgumentException("Intervall muss groesser als 0 sein");
        }
    }

    private void validateParameters(AbstractBatchJob job, Map<String, Object> params) {
        List<JobParameter> declaredParams = job.getParameters();
        for (JobParameter param : declaredParams) {
            if (param.isRequired() && !params.containsKey(param.getName())) {
                throw new IllegalArgumentException(
                        "Pflichtparameter fehlt: " + param.getName() + " (" + param.getDescription() + ")");
            }
        }
    }

    private ScheduleType parseScheduleType(String type) {
        if (type == null) return ScheduleType.NONE;
        try {
            return ScheduleType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltiger Schedule-Typ: " + type);
        }
    }

    // --- Hilfsmethoden ---

    private AbstractBatchJob findJobByKey(String jobKey) {
        return batchJobs.stream()
                .filter(j -> j.getJobKey().equals(jobKey))
                .findFirst()
                .orElse(null);
    }

    private BatchScheduleDto toDto(BatchScheduleEntity entity) {
        BatchScheduleDto dto = new BatchScheduleDto();
        dto.setId(entity.getId());
        dto.setJobKey(entity.getJobKey());
        dto.setName(entity.getName());
        dto.setScheduleType(entity.getScheduleType() != null ? entity.getScheduleType().name() : "NONE");
        dto.setCronExpression(entity.getCronExpression());
        dto.setIntervalSeconds(entity.getIntervalSeconds());
        dto.setEnabled(entity.isEnabled());
        dto.setParameters(fromJson(entity.getParameters()));
        return dto;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Fehler beim Serialisieren der Parameter", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Konnte Parameter-JSON nicht parsen: {}", json, e);
            return new HashMap<>();
        }
    }
}
