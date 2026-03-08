package de.market.scheduling.service;

import de.market.scheduling.jobs.AbstractBatchJob;
import de.market.scheduling.model.BatchScheduleEntity;
import de.market.scheduling.model.ScheduleType;
import de.market.scheduling.repository.BatchScheduleJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JobRegistry {

    private static final Logger log = LoggerFactory.getLogger(JobRegistry.class);

    private final List<AbstractBatchJob> jobs;
    private final BatchScheduleJpaRepository scheduleRepository;
    private final SchedulingService schedulingService;

    public JobRegistry(List<AbstractBatchJob> jobs,
                       BatchScheduleJpaRepository scheduleRepository,
                       SchedulingService schedulingService) {
        this.jobs = jobs;
        this.scheduleRepository = scheduleRepository;
        this.schedulingService = schedulingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("JobRegistry: {} Job-Typen im Code registriert", jobs.size());

        Set<String> codeJobKeys = jobs.stream()
                .map(AbstractBatchJob::getJobKey)
                .collect(Collectors.toSet());

        // Deactivate schedules for removed jobs
        List<BatchScheduleEntity> allSchedules = scheduleRepository.findAll();
        for (BatchScheduleEntity entity : allSchedules) {
            if (!codeJobKeys.contains(entity.getJobKey())) {
                if (entity.isEnabled()) {
                    entity.setEnabled(false);
                    entity.setScheduleType(ScheduleType.NONE);
                    scheduleRepository.save(entity);
                    log.warn("  Schedule deaktiviert (Job nicht mehr im Code): id={}, jobKey={}, name={}",
                            entity.getId(), entity.getJobKey(), entity.getName());
                }
            }
        }

        // Create Quartz triggers for all enabled schedules
        for (BatchScheduleEntity entity : scheduleRepository.findAll()) {
            schedulingService.updateQuartzTrigger(entity);
        }

        log.info("JobRegistry: Startup-Sync abgeschlossen ({} Schedules in DB)", allSchedules.size());
    }
}
