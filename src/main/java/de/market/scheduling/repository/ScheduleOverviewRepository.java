package de.market.scheduling.repository;

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
public class ScheduleOverviewRepository {

    private final DSLContext dsl;

    public ScheduleOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> findAllAsRows() {
        var e = BATCH_JOB_EXECUTION_LOG.as("e");
        var e2 = BATCH_JOB_EXECUTION_LOG.as("e2");
        var s = BATCH_SCHEDULE.as("s");

        Field<OffsetDateTime> lastRun = field(
                select(max(e.START_TIME))
                        .from(e)
                        .where(e.SCHEDULE_ID.eq(s.ID))
        ).as("lastRun");

        Field<String> lastStatus = field(
                select(e2.STATUS)
                        .from(e2)
                        .where(e2.SCHEDULE_ID.eq(s.ID))
                        .orderBy(e2.START_TIME.desc())
                        .limit(1)
        ).as("lastStatus");

        return dsl
                .select(
                        s.ID.as("id"),
                        s.JOB_KEY.as("jobKey"),
                        s.NAME.as("name"),
                        s.SCHEDULE_TYPE.as("scheduleType"),
                        s.ENABLED.as("enabled"),
                        s.CRON_EXPRESSION.as("cronExpression"),
                        s.INTERVAL_SECONDS.as("intervalSeconds"),
                        lastRun,
                        lastStatus
                )
                .from(s)
                .orderBy(s.NAME)
                .fetch()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.get("id"));
                    row.put("jobKey", r.get("jobKey"));
                    row.put("name", r.get("name"));
                    row.put("scheduleType", r.get("scheduleType"));
                    row.put("enabled", r.get("enabled"));
                    row.put("cronExpression", r.get("cronExpression"));
                    row.put("intervalSeconds", r.get("intervalSeconds"));
                    Object lr = r.get("lastRun");
                    row.put("lastRun", lr != null ? lr.toString() : null);
                    row.put("lastStatus", r.get("lastStatus"));
                    return row;
                });
    }
}
