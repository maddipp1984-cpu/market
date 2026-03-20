package de.market.timeseries.repository;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Oracle-spezifische Zeitreihen-Operationen (Stub).
 * Nutzt PL/SQL Stored Procedures und Oracle TABLE-Types.
 *
 * Aktiviert ueber: spring.profiles.active=oracle
 */
@Repository
@Profile("oracle")
public class OracleTimeSeriesProcedures implements TimeSeriesProcedures {

    private final DSLContext dsl;

    public OracleTimeSeriesProcedures(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public int writeYear(long tsId, TimeDimension dim, int year, double[] values) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to, double[] values) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public Map<LocalDate, double[]> readSubdaily(long tsId, TimeDimension dim,
                                                  LocalDate firstDay, LocalDate lastDayExcl) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public Map<LocalDate, double[]> readSumViaStoredProc(List<Long> tsIds, TimeDimension dim,
                                                          LocalDate firstDay, LocalDate lastDayExcl) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public Map<LocalDate, double[]> readSumViaJava(List<Long> tsIds, TimeDimension dim,
                                                     LocalDate firstDay, LocalDate lastDayExcl) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public TimeSeriesSlice readSumSimple(List<Long> tsIds, TimeDimension dim,
                                          LocalDateTime start, LocalDateTime end) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }

    @Override
    public int deleteSubdaily(long tsId, TimeDimension dim, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("Oracle-Implementierung noch nicht vorhanden");
    }
}
