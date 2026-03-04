package de.projekt.timeseries.client;

import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TimeSeriesSlice;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Öffentliche Entwickler-API zum Lesen von Zeitreihen.
 * Unterstützt automatische Dimensionskonvertierung (Aggregation/Disaggregation).
 */
public class TimeSeriesClient {

    private static final AggregationFunction DEFAULT_FUNCTION = AggregationFunction.SUM;

    private final TimeSeriesService service;

    public TimeSeriesClient(TimeSeriesService service) {
        this.service = service;
    }

    /**
     * Liest eine Zeitreihe in ihrer nativen Dimension.
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        return service.read(tsId, start, end);
    }

    /**
     * Liest eine Zeitreihe und konvertiert in die Zieldimension (Default: SUM).
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                TimeDimension targetDimension) throws SQLException {
        return read(tsId, start, end, targetDimension, DEFAULT_FUNCTION);
    }

    /**
     * Liest eine Zeitreihe und konvertiert in die Zieldimension mit der angegebenen Funktion.
     *
     * @param tsId            ID der Zeitreihe
     * @param start           Beginn (inklusiv)
     * @param end             Ende (exklusiv)
     * @param targetDimension Zieldimension (null = native Dimension)
     * @param function        Aggregationsfunktion
     */
    public TimeSeriesSlice read(long tsId, LocalDateTime start, LocalDateTime end,
                                TimeDimension targetDimension,
                                AggregationFunction function) throws SQLException {
        TimeSeriesHeader header = service.getHeader(tsId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Zeitreihe nicht gefunden: tsId=" + tsId));

        TimeDimension nativeDim = header.getTimeDimension();
        TimeDimension target = targetDimension != null ? targetDimension : nativeDim;
        AggregationFunction func = function != null ? function : DEFAULT_FUNCTION;

        // Keine Konvertierung nötig
        if (target == nativeDim) {
            return service.read(tsId, start, end);
        }

        // Rohdaten lesen
        TimeSeriesSlice raw = service.read(tsId, start, end);

        // Konvertieren
        if (nativeDim.canAggregateTo(target)) {
            return DimensionConverter.aggregate(raw, target, func);
        } else if (nativeDim.canDisaggregateTo(target)) {
            return DimensionConverter.disaggregate(raw, target, func);
        } else {
            throw new IllegalArgumentException(
                    "Konvertierung von " + nativeDim + " nach " + target + " nicht möglich");
        }
    }
}
