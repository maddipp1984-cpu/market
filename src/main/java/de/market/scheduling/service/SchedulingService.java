package de.market.scheduling.service;

import de.market.scheduling.jobs.QuartzJobAdapter;
import de.market.scheduling.model.JobDefinitionEntity;
import de.market.scheduling.model.ScheduleType;
import de.market.scheduling.repository.JobDefinitionRepository;
import de.market.scheduling.repository.JobExecutionLogRepository;
import de.market.scheduling.repository.JobOverviewRepository;
import de.market.scheduling.rest.dto.BatchJobDto;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final JobDefinitionRepository definitionRepository;
    private final JobOverviewRepository overviewRepository;
    private final JobExecutionLogRepository executionLogRepository;
    private final Scheduler scheduler;

    public SchedulingService(JobDefinitionRepository definitionRepository,
                              JobOverviewRepository overviewRepository,
                              JobExecutionLogRepository executionLogRepository,
                              Scheduler scheduler) {
        this.definitionRepository = definitionRepository;
        this.overviewRepository = overviewRepository;
        this.executionLogRepository = executionLogRepository;
        this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        return overviewRepository.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public BatchJobDto findById(int id) {
        JobDefinitionEntity entity = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public BatchJobDto update(int id, BatchJobDto dto) {
        JobDefinitionEntity entity = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job nicht gefunden: id=" + id));

        entity.setEnabled(dto.isEnabled());
        entity.setScheduleType(dto.getScheduleType());
        entity.setCronExpression(dto.getCronExpression());
        entity.setIntervalSeconds(dto.getIntervalSeconds());

        // Validate
        if (entity.getScheduleType() == ScheduleType.CRON && (entity.getCronExpression() == null || entity.getCronExpression().isBlank())) {
            throw new IllegalArgumentException("Cron-Expression ist Pflichtfeld bei Schedule-Typ CRON");
        }
        if (entity.getScheduleType() == ScheduleType.INTERVAL && (entity.getIntervalSeconds() == null || entity.getIntervalSeconds() <= 0)) {
            throw new IllegalArgumentException("Intervall muss groesser als 0 sein");
        }

        entity = definitionRepository.save(entity);

        // Update Quartz trigger
        updateQuartzTrigger(entity);

        return toDto(entity);
    }

    public void triggerManually(int id) {
        JobDefinitionEntity entity = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job nicht gefunden: id=" + id));

        try {
            JobKey jobKey = JobKey.jobKey(entity.getJobKey(), "batch-jobs");

            // Ensure the job is registered in Quartz
            if (!scheduler.checkExists(jobKey)) {
                JobDetail jobDetail = buildJobDetail(entity);
                scheduler.addJob(jobDetail, true);
            }

            // Trigger with MANUAL flag
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("definitionId", entity.getId());
            dataMap.put("triggeredBy", "MANUAL");
            scheduler.triggerJob(jobKey, dataMap);

            log.info("Job {} manuell ausgeloest", entity.getJobKey());
        } catch (SchedulerException e) {
            throw new RuntimeException("Konnte Job nicht ausloesen: " + entity.getJobKey(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(int id, int limit) throws SQLException {
        if (!definitionRepository.existsById(id)) {
            throw new IllegalArgumentException("Job nicht gefunden: id=" + id);
        }
        int effectiveLimit = Math.max(1, Math.min(limit, 200));
        return executionLogRepository.findByDefinitionId(id, effectiveLimit);
    }

    public String getLogContent(int id, long executionId) {
        JobDefinitionEntity entity = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job nicht gefunden: id=" + id));
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

    // Called by JobRegistry to register/update Quartz triggers
    public void updateQuartzTrigger(JobDefinitionEntity entity) {
        try {
            JobKey jobKey = JobKey.jobKey(entity.getJobKey(), "batch-jobs");
            TriggerKey triggerKey = TriggerKey.triggerKey(entity.getJobKey(), "batch-jobs");

            if (!entity.isEnabled() || entity.getScheduleType() == ScheduleType.NONE) {
                // Remove trigger if exists
                if (scheduler.checkExists(triggerKey)) {
                    scheduler.unscheduleJob(triggerKey);
                    log.info("Trigger entfernt fuer Job {}", entity.getJobKey());
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
            Trigger trigger = buildTrigger(entity, triggerKey);
            if (trigger == null) return;

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
                log.info("Trigger aktualisiert fuer Job {} ({})", entity.getJobKey(), entity.getScheduleType());
            } else {
                scheduler.scheduleJob(trigger);
                log.info("Trigger erstellt fuer Job {} ({})", entity.getJobKey(), entity.getScheduleType());
            }
        } catch (SchedulerException e) {
            log.error("Fehler beim Aktualisieren des Triggers fuer Job {}", entity.getJobKey(), e);
        }
    }

    private JobDetail buildJobDetail(JobDefinitionEntity entity) {
        return JobBuilder.newJob(QuartzJobAdapter.class)
                .withIdentity(entity.getJobKey(), "batch-jobs")
                .usingJobData("definitionId", entity.getId())
                .usingJobData("triggeredBy", "SCHEDULER")
                .storeDurably(true)
                .build();
    }

    private Trigger buildTrigger(JobDefinitionEntity entity, TriggerKey triggerKey) {
        if (entity.getScheduleType() == ScheduleType.CRON) {
            try {
                return TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .forJob(entity.getJobKey(), "batch-jobs")
                        .withSchedule(CronScheduleBuilder.cronSchedule(entity.getCronExpression()))
                        .build();
            } catch (Exception e) {
                log.error("Ungueltige Cron-Expression fuer Job {}: {}", entity.getJobKey(), entity.getCronExpression());
                return null;
            }
        } else if (entity.getScheduleType() == ScheduleType.INTERVAL) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(entity.getJobKey(), "batch-jobs")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds(entity.getIntervalSeconds())
                            .repeatForever())
                    .build();
        }
        return null;
    }

    private BatchJobDto toDto(JobDefinitionEntity entity) {
        BatchJobDto dto = new BatchJobDto();
        dto.setId(entity.getId());
        dto.setJobKey(entity.getJobKey());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setJobClass(entity.getJobClass());
        dto.setScheduleType(entity.getScheduleType());
        dto.setCronExpression(entity.getCronExpression());
        dto.setIntervalSeconds(entity.getIntervalSeconds());
        dto.setEnabled(entity.isEnabled());
        return dto;
    }
}
