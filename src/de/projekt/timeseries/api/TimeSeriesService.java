package de.projekt.timeseries.api;

import de.projekt.timeseries.repository.HeaderRepository;
import de.projekt.timeseries.repository.TimeSeriesRepository;
import de.projekt.timeseries.model.Currency;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TimeSeriesSlice;
import de.projekt.timeseries.model.Unit;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class TimeSeriesService {

    private final HeaderRepository headerRepo;
    private final TimeSeriesRepository tsRepo;

    public TimeSeriesService(HeaderRepository headerRepo, TimeSeriesRepository tsRepo) {
        this.headerRepo = headerRepo;
        this.tsRepo = tsRepo;
    }

    // ================================================================
    // Header-Operationen
    // ================================================================

    public long createTimeSeries(String key, TimeDimension dimension, Unit unit) throws SQLException {
        TimeSeriesHeader header = new TimeSeriesHeader(key, dimension, unit);
        return headerRepo.create(header);
    }

    public long createTimeSeries(String key, TimeDimension dimension, Unit unit,
                                 String description) throws SQLException {
        TimeSeriesHeader header = new TimeSeriesHeader(key, dimension, unit);
        header.setDescription(description);
        return headerRepo.create(header);
    }

    public long createTimeSeries(String key, TimeDimension dimension, Unit unit,
                                 Currency currency, String description) throws SQLException {
        TimeSeriesHeader header = new TimeSeriesHeader(key, dimension, unit, currency);
        header.setDescription(description);
        return headerRepo.create(header);
    }

    public Optional<TimeSeriesHeader> getHeader(long tsId) throws SQLException {
        return headerRepo.findById(tsId);
    }

    public Optional<TimeSeriesHeader> getHeader(String tsKey) throws SQLException {
        return headerRepo.findByKey(tsKey);
    }

    // ================================================================
    // Schreiben: 1/4h und 1h (horizontal, über Stored Procedures)
    // ================================================================

    /**
     * Schreibt einen Tag. DST wird serverseitig validiert.
     */
    public void writeDay(long tsId, LocalDate date, double[] values) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        tsRepo.writeDay(tsId, h.getTimeDimension(), date, values);
    }

    /**
     * Schreibt ein ganzes Jahr (EIN DB-Aufruf).
     * @return Anzahl geschriebener Tage (365 oder 366)
     */
    public int writeYear(long tsId, int year, double[] values) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.writeYear(tsId, h.getTimeDimension(), year, values);
    }

    // ================================================================
    // Lesen
    // ================================================================

    /**
     * Liest als TimeSeriesSlice (flaches double[]).
     * Timestamps bei Bedarf über slice.getTimestamp(index).
     *
     * @param start Beginn (inklusiv, mit Uhrzeit)
     * @param end   Ende (exklusiv, mit Uhrzeit)
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.read(tsId, h.getTimeDimension(), start, end);
    }

    // ================================================================
    // Löschen / Zählen
    // ================================================================

    public int deleteAll(long tsId) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.delete(tsId, h.getTimeDimension());
    }

    public long count(long tsId) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.count(tsId, h.getTimeDimension());
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private TimeSeriesHeader requireHeader(long tsId) throws SQLException {
        return headerRepo.findById(tsId)
                .orElseThrow(() -> new IllegalArgumentException("Zeitreihe nicht gefunden: tsId=" + tsId));
    }
}
