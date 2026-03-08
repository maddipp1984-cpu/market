package de.market.scheduling.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.market.scheduling.model.JobResult;
import de.market.scheduling.model.JobStatus;
import de.market.scheduling.repository.JobExecutionLogRepository;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class QuartzJobAdapter implements Job {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobAdapter.class);

    @Autowired
    private JobExecutionLogRepository executionLogRepository;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String jobKey = context.getJobDetail().getKey().getName();
        int scheduleId = dataMap.getInt("scheduleId");
        String triggeredBy = dataMap.getString("triggeredBy");
        if (triggeredBy == null) triggeredBy = "SCHEDULER";

        // Extract parameters from JobDataMap
        Map<String, Object> parameters = extractParameters(dataMap);

        // Insert execution log (RUNNING)
        long executionId;
        try {
            executionId = executionLogRepository.insertExecution(
                    scheduleId, OffsetDateTime.now(), JobStatus.RUNNING, triggeredBy);
        } catch (Exception e) {
            log.error("Konnte Execution-Log nicht anlegen fuer Job {}", jobKey, e);
            throw new JobExecutionException(e);
        }

        String logFileName = jobKey + "_" + executionId;
        MDC.put("jobExecution", logFileName);
        log.info("Job {} gestartet (executionId={}, scheduleId={})", jobKey, executionId, scheduleId);

        try {
            // Find the AbstractBatchJob bean
            AbstractBatchJob batchJob = findBatchJob(jobKey);
            if (batchJob == null) {
                throw new IllegalStateException("Kein AbstractBatchJob-Bean fuer Key: " + jobKey);
            }

            batchJob.validateParameters(parameters);
            JobResult result = batchJob.execute(parameters);
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

    private Map<String, Object> extractParameters(JobDataMap dataMap) {
        String parametersJson = dataMap.getString("parameters");
        if (parametersJson == null || parametersJson.isBlank() || parametersJson.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Konnte Parameter-JSON nicht parsen: {}", parametersJson, e);
            return new HashMap<>();
        }
    }

    private AbstractBatchJob findBatchJob(String jobKey) {
        return applicationContext.getBeansOfType(AbstractBatchJob.class).values().stream()
                .filter(job -> job.getJobKey().equals(jobKey))
                .findFirst()
                .orElse(null);
    }
}
