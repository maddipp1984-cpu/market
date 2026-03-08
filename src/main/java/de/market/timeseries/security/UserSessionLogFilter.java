package de.market.timeseries.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback-Filter: laesst nur Log-Events durch, bei denen ein
 * authentifizierter User im MDC steht (userSession != null).
 * Verhindert, dass System-Logs (Startup, Scheduler) in User-Files landen.
 */
public class UserSessionLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String userSession = event.getMDCPropertyMap().get("userSession");
        return userSession != null ? FilterReply.NEUTRAL : FilterReply.DENY;
    }
}
