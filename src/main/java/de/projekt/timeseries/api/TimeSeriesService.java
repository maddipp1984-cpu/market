package de.projekt.timeseries.api;

import de.projekt.timeseries.repository.HeaderRepository;
import de.projekt.timeseries.repository.ObjectRepository;
import de.projekt.timeseries.repository.TimeSeriesRepository;
import de.projekt.timeseries.model.Currency;
import de.projekt.timeseries.model.ObjectType;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TimeSeriesSlice;
import de.projekt.timeseries.model.TsObject;
import de.projekt.timeseries.model.Unit;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class TimeSeriesService {

    private final HeaderRepository headerRepo;
    private final ObjectRepository objectRepo;
    private final TimeSeriesRepository tsRepo;

    public TimeSeriesService(HeaderRepository headerRepo, ObjectRepository objectRepo,
                             TimeSeriesRepository tsRepo) {
        this.headerRepo = headerRepo;
        this.objectRepo = objectRepo;
        this.tsRepo = tsRepo;
    }

    public TimeSeriesService(HeaderRepository headerRepo, TimeSeriesRepository tsRepo) {
        this(headerRepo, null, tsRepo);
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

    /**
     * Schreibt einen Einzelwert für Tag/Monat/Jahr (Upsert).
     */
    public void writeSimple(long tsId, LocalDate date, double value) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        tsRepo.writeSimple(tsId, h.getTimeDimension(), date, value);
    }

    /**
     * Schreibt einen Einzelwert für ein Jahr (Upsert).
     */
    public void writeSimple(long tsId, int year, double value) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        tsRepo.writeSimple(tsId, h.getTimeDimension(), LocalDate.of(year, 1, 1), value);
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

    /**
     * Löscht eine Zeitreihe komplett (Werte + Header).
     */
    public void deleteTimeSeries(long tsId) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        tsRepo.delete(tsId, h.getTimeDimension());
        headerRepo.delete(tsId);
    }

    /**
     * Löscht alle Werte einer Zeitreihe (Header bleibt erhalten).
     */
    public int deleteValues(long tsId) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.delete(tsId, h.getTimeDimension());
    }

    /**
     * Löscht Werte in einem Zeitraum [from, to).
     * Kleinste Einheit ist ein Tag (subdaily-Dimensionen speichern tageweise).
     * @param from Beginn (inklusiv)
     * @param to   Ende (exklusiv)
     * @return Anzahl gelöschter Zeilen/Tage
     */
    public int delete(long tsId, LocalDate from, LocalDate to) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.delete(tsId, h.getTimeDimension(), from, to);
    }

    public long count(long tsId) throws SQLException {
        TimeSeriesHeader h = requireHeader(tsId);
        return tsRepo.count(tsId, h.getTimeDimension());
    }

    // ================================================================
    // Objekt-Operationen
    // ================================================================

    public long createObject(ObjectType type, String key, String description) throws SQLException {
        requireObjectRepo();
        TsObject obj = new TsObject(type, key, description);
        return objectRepo.create(obj);
    }

    public Optional<TsObject> getObject(long objectId) throws SQLException {
        requireObjectRepo();
        return objectRepo.findById(objectId);
    }

    public Optional<TsObject> getObject(String objectKey) throws SQLException {
        requireObjectRepo();
        return objectRepo.findByKey(objectKey);
    }

    public List<TsObject> getObjectsByType(ObjectType type) throws SQLException {
        requireObjectRepo();
        return objectRepo.findByType(type);
    }

    public List<TimeSeriesHeader> getTimeSeriesByObject(long objectId) throws SQLException {
        return headerRepo.findByObjectId(objectId);
    }

    public void assignToObject(long tsId, long objectId) throws SQLException {
        requireObjectRepo();
        requireObject(objectId);
        requireHeader(tsId);  // prüft nur Existenz
        headerRepo.updateObjectId(tsId, objectId);
    }

    public void removeFromObject(long tsId) throws SQLException {
        requireHeader(tsId);  // prüft nur Existenz
        headerRepo.updateObjectId(tsId, null);
    }

    public void updateObject(TsObject object) throws SQLException {
        requireObjectRepo();
        if (!objectRepo.update(object)) {
            throw new IllegalArgumentException("Objekt nicht gefunden: objectId=" + object.getObjectId());
        }
    }

    public boolean deleteObject(long objectId) throws SQLException {
        requireObjectRepo();
        List<TimeSeriesHeader> assigned = headerRepo.findByObjectId(objectId);
        if (!assigned.isEmpty()) {
            throw new IllegalStateException("Objekt hat noch " + assigned.size()
                    + " zugeordnete Zeitreihe(n), zuerst Zuordnungen entfernen");
        }
        return objectRepo.delete(objectId);
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private TimeSeriesHeader requireHeader(long tsId) throws SQLException {
        return headerRepo.findById(tsId)
                .orElseThrow(() -> new IllegalArgumentException("Zeitreihe nicht gefunden: tsId=" + tsId));
    }

    private TsObject requireObject(long objectId) throws SQLException {
        return objectRepo.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Objekt nicht gefunden: objectId=" + objectId));
    }

    private void requireObjectRepo() {
        if (objectRepo == null) {
            throw new IllegalStateException("ObjectRepository nicht konfiguriert");
        }
    }
}
