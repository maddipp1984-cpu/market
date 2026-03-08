package de.market.scheduling.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback-Filter: laesst nur Log-Events durch, bei denen
 * jobExecution im MDC gesetzt ist. Verhindert, dass normale
 * Log-Zeilen in logs/jobs/_none.log landen.
 */
public class JobExecutionLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String jobExecution = event.getMDCPropertyMap().get("jobExecution");
        return jobExecution != null ? FilterReply.NEUTRAL : FilterReply.DENY;
    }
}
