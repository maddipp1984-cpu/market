package de.market.scheduling.repository;

import de.market.scheduling.model.JobStatus;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.BatchJobExecutionLog.BATCH_JOB_EXECUTION_LOG;
import static de.market.jooq.generated.tables.BatchSchedule.BATCH_SCHEDULE;
import static org.jooq.impl.DSL.*;

@Repository
public class JobExecutionLogRepository {

    private final DSLContext dsl;

    public JobExecutionLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long insertExecution(int scheduleId, OffsetDateTime startTime,
                                 JobStatus status, String triggeredBy) {
        return dsl
                .insertInto(BATCH_JOB_EXECUTION_LOG)
                .set(BATCH_JOB_EXECUTION_LOG.SCHEDULE_ID, scheduleId)
                .set(BATCH_JOB_EXECUTION_LOG.START_TIME, startTime)
                .set(BATCH_JOB_EXECUTION_LOG.STATUS, status.name())
                .set(BATCH_JOB_EXECUTION_LOG.TRIGGERED_BY, triggeredBy)
                .returning(BATCH_JOB_EXECUTION_LOG.ID)
                .fetchOne()
                .get(BATCH_JOB_EXECUTION_LOG.ID);
    }

    public void updateExecution(long executionId, OffsetDateTime endTime,
                                 JobStatus status, String errorMessage,
                                 Integer recordsAffected, String logFile) {
        dsl.update(BATCH_JOB_EXECUTION_LOG)
                .set(BATCH_JOB_EXECUTION_LOG.END_TIME, endTime)
                .set(BATCH_JOB_EXECUTION_LOG.STATUS, status.name())
                .set(BATCH_JOB_EXECUTION_LOG.ERROR_MESSAGE, errorMessage)
                .set(BATCH_JOB_EXECUTION_LOG.RECORDS_AFFECTED, recordsAffected)
                .set(BATCH_JOB_EXECUTION_LOG.LOG_FILE, logFile)
                .where(BATCH_JOB_EXECUTION_LOG.ID.eq(executionId))
                .execute();
    }

    public List<Map<String, Object>> findByScheduleId(int scheduleId, int limit) {
        var e = BATCH_JOB_EXECUTION_LOG.as("e");
        var s = BATCH_SCHEDULE.as("s");

        Field<Integer> duration = field(
                "CASE WHEN {0} IS NOT NULL THEN EXTRACT(EPOCH FROM ({0} - {1}))::INTEGER ELSE NULL END",
                Integer.class, e.END_TIME, e.START_TIME
        ).as("duration");

        return dsl
                .select(
                        e.ID.as("id"),
                        e.SCHEDULE_ID.as("scheduleId"),
                        s.NAME.as("scheduleName"),
                        e.START_TIME.as("startTime"),
                        e.END_TIME.as("endTime"),
                        duration,
                        e.STATUS.as("status"),
                        e.ERROR_MESSAGE.as("errorMessage"),
                        e.RECORDS_AFFECTED.as("recordsAffected"),
                        e.LOG_FILE.as("logFile"),
                        e.TRIGGERED_BY.as("triggeredBy")
                )
                .from(e)
                .join(s).on(s.ID.eq(e.SCHEDULE_ID))
                .where(e.SCHEDULE_ID.eq(scheduleId))
                .orderBy(e.START_TIME.desc())
                .limit(limit)
                .fetch()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.get("id"));
                    row.put("scheduleId", r.get("scheduleId"));
                    row.put("scheduleName", r.get("scheduleName"));
                    Object st = r.get("startTime");
                    row.put("startTime", st != null ? st.toString() : null);
                    Object et = r.get("endTime");
                    row.put("endTime", et != null ? et.toString() : null);
                    row.put("duration", r.get("duration"));
                    row.put("status", r.get("status"));
                    row.put("errorMessage", r.get("errorMessage"));
                    row.put("recordsAffected", r.get("recordsAffected"));
                    row.put("logFile", r.get("logFile"));
                    row.put("triggeredBy", r.get("triggeredBy"));
                    return row;
                });
    }

    public String findLogFileByExecutionId(long executionId) {
        return dsl
                .select(BATCH_JOB_EXECUTION_LOG.LOG_FILE)
                .from(BATCH_JOB_EXECUTION_LOG)
                .where(BATCH_JOB_EXECUTION_LOG.ID.eq(executionId))
                .fetchOne(BATCH_JOB_EXECUTION_LOG.LOG_FILE);
    }

    public List<Map<String, Object>> findAllAsRows(int limit) {
        var e = BATCH_JOB_EXECUTION_LOG.as("e");
        var s = BATCH_SCHEDULE.as("s");

        Field<Integer> duration = field(
                "CASE WHEN {0} IS NOT NULL THEN EXTRACT(EPOCH FROM ({0} - {1}))::INTEGER ELSE NULL END",
                Integer.class, e.END_TIME, e.START_TIME
        ).as("duration");

        return dsl
                .select(
                        e.ID.as("id"),
                        e.SCHEDULE_ID.as("scheduleId"),
                        s.JOB_KEY.as("jobKey"),
                        s.NAME.as("scheduleName"),
                        e.START_TIME.as("startTime"),
                        e.END_TIME.as("endTime"),
                        duration,
                        e.STATUS.as("status"),
                        e.ERROR_MESSAGE.as("errorMessage"),
                        e.RECORDS_AFFECTED.as("recordsAffected"),
                        e.LOG_FILE.as("logFile"),
                        e.TRIGGERED_BY.as("triggeredBy")
                )
                .from(e)
                .join(s).on(s.ID.eq(e.SCHEDULE_ID))
                .orderBy(e.START_TIME.desc())
                .limit(limit)
                .fetch()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.get("id"));
                    row.put("scheduleId", r.get("scheduleId"));
                    row.put("jobKey", r.get("jobKey"));
                    row.put("scheduleName", r.get("scheduleName"));
                    Object st = r.get("startTime");
                    row.put("startTime", st != null ? st.toString() : null);
                    Object et = r.get("endTime");
                    row.put("endTime", et != null ? et.toString() : null);
                    row.put("duration", r.get("duration"));
                    row.put("status", r.get("status"));
                    row.put("errorMessage", r.get("errorMessage"));
                    row.put("recordsAffected", r.get("recordsAffected"));
                    row.put("logFile", r.get("logFile"));
                    row.put("triggeredBy", r.get("triggeredBy"));
                    return row;
                });
    }
}
