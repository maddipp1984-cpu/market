package de.market.scheduling.service;

import de.market.scheduling.jobs.AbstractBatchJob;
import de.market.scheduling.model.JobDefinitionEntity;
import de.market.scheduling.model.ScheduleType;
import de.market.scheduling.repository.JobDefinitionRepository;
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
    private final JobDefinitionRepository definitionRepository;
    private final SchedulingService schedulingService;

    public JobRegistry(List<AbstractBatchJob> jobs,
                       JobDefinitionRepository definitionRepository,
                       SchedulingService schedulingService) {
        this.jobs = jobs;
        this.definitionRepository = definitionRepository;
        this.schedulingService = schedulingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncJobsOnStartup() {
        log.info("JobRegistry: Synchronisiere {} registrierte Jobs mit DB", jobs.size());

        Set<String> codeJobKeys = jobs.stream()
                .map(AbstractBatchJob::getJobKey)
                .collect(Collectors.toSet());

        // Sync code -> DB
        for (AbstractBatchJob job : jobs) {
            var existing = definitionRepository.findByJobKey(job.getJobKey());
            if (existing.isEmpty()) {
                // New job -> INSERT
                JobDefinitionEntity entity = new JobDefinitionEntity();
                entity.setJobKey(job.getJobKey());
                entity.setName(job.getName());
                entity.setDescription(job.getDescription());
                entity.setJobClass(job.getClass().getName());
                entity.setScheduleType(ScheduleType.NONE);
                entity.setEnabled(false);
                definitionRepository.save(entity);
                log.info("  Neuer Job registriert: {} ({})", job.getJobKey(), job.getName());
            } else {
                // Update name/description/class if changed
                JobDefinitionEntity entity = existing.get();
                boolean changed = false;
                if (!entity.getName().equals(job.getName())) {
                    entity.setName(job.getName());
                    changed = true;
                }
                if (!java.util.Objects.equals(entity.getDescription(), job.getDescription())) {
                    entity.setDescription(job.getDescription());
                    changed = true;
                }
                if (!entity.getJobClass().equals(job.getClass().getName())) {
                    entity.setJobClass(job.getClass().getName());
                    changed = true;
                }
                if (changed) {
                    definitionRepository.save(entity);
                    log.info("  Job aktualisiert: {}", job.getJobKey());
                }
            }
        }

        // Deactivate removed jobs
        List<JobDefinitionEntity> allDefinitions = definitionRepository.findAll();
        for (JobDefinitionEntity entity : allDefinitions) {
            if (!codeJobKeys.contains(entity.getJobKey()) && entity.isEnabled()) {
                entity.setEnabled(false);
                entity.setScheduleType(ScheduleType.NONE);
                definitionRepository.save(entity);
                log.warn("  Job deaktiviert (nicht mehr im Code): {}", entity.getJobKey());
            }
        }

        // Create Quartz triggers for enabled jobs
        for (JobDefinitionEntity entity : definitionRepository.findAll()) {
            schedulingService.updateQuartzTrigger(entity);
        }

        log.info("JobRegistry: Synchronisierung abgeschlossen");
    }
}
