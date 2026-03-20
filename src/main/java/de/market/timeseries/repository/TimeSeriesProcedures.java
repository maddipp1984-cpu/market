package de.market.timeseries.repository;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DB-spezifische Operationen fuer Zeitreihen.
 * Stored Procedures, Array-Parameter und andere Konstrukte die
 * nicht ueber jOOQ DSL abstrahiert werden koennen.
 *
 * Pro Datenbank eine Implementierung: PostgresTimeSeriesProcedures, OracleTimeSeriesProcedures.
 */
public interface TimeSeriesProcedures {

    // Schreiben (subdaily: 15min/1h, Stored Procedures mit Array-Parametern)
    void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values);
    int writeYear(long tsId, TimeDimension dim, int year, double[] values);
    int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to, double[] values);

    // Lesen (subdaily: Array-Rueckgabe aus DB)
    Map<LocalDate, double[]> readSubdaily(long tsId, TimeDimension dim, LocalDate firstDay, LocalDate lastDayExcl);

    // Aggregation (subdaily: Stored Procedures oder Bulk-Read mit Arrays)
    Map<LocalDate, double[]> readSumViaStoredProc(List<Long> tsIds, TimeDimension dim, LocalDate firstDay, LocalDate lastDayExcl);
    Map<LocalDate, double[]> readSumViaJava(List<Long> tsIds, TimeDimension dim, LocalDate firstDay, LocalDate lastDayExcl);
    TimeSeriesSlice readSumSimple(List<Long> tsIds, TimeDimension dim, LocalDateTime start, LocalDateTime end);

    // Loeschen (subdaily: Stored Procedures)
    int deleteSubdaily(long tsId, TimeDimension dim, LocalDate from, LocalDate to);
}
