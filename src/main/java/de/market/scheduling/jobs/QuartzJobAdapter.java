package de.market.scheduling.jobs;

import de.market.scheduling.model.JobResult;
import de.market.scheduling.model.JobStatus;
import de.market.scheduling.repository.JobExecutionLogRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

public class QuartzJobAdapter implements Job {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobAdapter.class);

    @Autowired
    private JobExecutionLogRepository executionLogRepository;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobKey = context.getJobDetail().getKey().getName();
        int definitionId = context.getMergedJobDataMap().getInt("definitionId");
        String triggeredBy = context.getMergedJobDataMap().getString("triggeredBy");
        if (triggeredBy == null) triggeredBy = "SCHEDULER";

        // Insert execution log (RUNNING)
        long executionId;
        try {
            executionId = executionLogRepository.insertExecution(
                    definitionId, OffsetDateTime.now(), JobStatus.RUNNING, triggeredBy);
        } catch (Exception e) {
            log.error("Konnte Execution-Log nicht anlegen fuer Job {}", jobKey, e);
            throw new JobExecutionException(e);
        }

        String logFileName = jobKey + "_" + executionId;
        MDC.put("jobExecution", logFileName);
        log.info("Job {} gestartet (executionId={})", jobKey, executionId);

        try {
            // Find the AbstractBatchJob bean
            AbstractBatchJob batchJob = findBatchJob(jobKey);
            if (batchJob == null) {
                throw new IllegalStateException("Kein AbstractBatchJob-Bean fuer Key: " + jobKey);
            }

            JobResult result = batchJob.execute();
            log.info("Job {} abgeschlossen: {} Datensaetze, {}", jobKey, result.recordsAffected(), result.message());

            executionLogRepository.updateExecution(executionId,
                    OffsetDateTime.now(), JobStatus.COMPLETED,
                    null, result.recordsAffected(), "logs/jobs/" + logFileName + ".log");

        } catch (Exception e) {
            log.error("Job {} fehlgeschlagen", jobKey, e);
            try {
                executionLogRepository.updateExecution(executionId,
                        OffsetDateTime.now(), JobStatus.FAILED,
                        e.getMessage(), null, "logs/jobs/" + logFileName + ".log");
            } catch (Exception logEx) {
                log.error("Konnte FAILED-Status nicht schreiben fuer executionId={}", executionId, logEx);
            }
        } finally {
            MDC.remove("jobExecution");
        }
    }

    private AbstractBatchJob findBatchJob(String jobKey) {
        return applicationContext.getBeansOfType(AbstractBatchJob.class).values().stream()
                .filter(job -> job.getJobKey().equals(jobKey))
                .findFirst()
                .orElse(null);
    }
}
