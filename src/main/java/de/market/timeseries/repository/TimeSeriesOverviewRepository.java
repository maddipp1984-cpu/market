package de.market.timeseries.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TimeSeriesOverviewRepository {

    // language=SQL
    private static final String BASE_SQL =
            "WITH value_range AS (\n" +
            "    SELECT ts_id, MIN(ts_date) AS first_date, MAX(ts_date) AS last_date FROM ts_values_15min GROUP BY ts_id\n" +
            "    UNION ALL\n" +
            "    SELECT ts_id, MIN(ts_date), MAX(ts_date) FROM ts_values_1h GROUP BY ts_id\n" +
            "    UNION ALL\n" +
            "    SELECT ts_id, MIN(ts_date), MAX(ts_date) FROM ts_values_day GROUP BY ts_id\n" +
            "    UNION ALL\n" +
            "    SELECT ts_id, MIN(ts_date), MAX(ts_date) FROM ts_values_month GROUP BY ts_id\n" +
            "    UNION ALL\n" +
            "    SELECT ts_id, (MIN(ts_year)::text || '-01-01')::date, (MAX(ts_year)::text || '-01-01')::date FROM ts_values_year GROUP BY ts_id\n" +
            ")\n" +
            "SELECT h.ts_id,\n" +
            "       h.ts_key,\n" +
            "       CASE h.time_dim\n" +
            "           WHEN 1 THEN '15 Minuten'\n" +
            "           WHEN 2 THEN '1 Stunde'\n" +
            "           WHEN 3 THEN 'Tag'\n" +
            "           WHEN 4 THEN 'Monat'\n" +
            "           WHEN 5 THEN 'Jahr'\n" +
            "       END AS dimension,\n" +
            "       u.symbol,\n" +
            "       c.iso_code,\n" +
            "       o.object_key,\n" +
            "       h.description,\n" +
            "       vr.first_date,\n" +
            "       vr.last_date,\n" +
            "       h.created_at,\n" +
            "       h.updated_at\n" +
            "FROM ts_header h\n" +
            "JOIN ts_unit u ON u.unit_id = h.unit_id\n" +
            "LEFT JOIN ts_currency c ON c.currency_id = h.currency_id\n" +
            "LEFT JOIN ts_object o ON o.object_id = h.object_id\n" +
            "LEFT JOIN value_range vr ON vr.ts_id = h.ts_id\n" +
            "ORDER BY h.ts_key";

    private final DSLContext dsl;

    public TimeSeriesOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> findAllAsRows() {
        Result<Record> records = dsl.resultQuery(BASE_SQL).fetch();
        return mapRecords(records);
    }

    public List<Map<String, Object>> findFiltered(Condition condition) {
        String whereSql = dsl.renderInlined(condition);
        String sql = BASE_SQL.replaceFirst("(?i)ORDER BY", "WHERE " + whereSql + "\nORDER BY");
        Result<Record> records = dsl.resultQuery(sql).fetch();
        return mapRecords(records);
    }

    private List<Map<String, Object>> mapRecords(Result<Record> records) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Record r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.get("ts_id"));
            row.put("key", r.get("ts_key"));
            row.put("dimension", r.get("dimension"));
            row.put("unit", r.get("symbol"));
            row.put("currency", r.get("iso_code"));
            row.put("object", r.get("object_key"));
            row.put("description", r.get("description"));
            row.put("firstDate", r.get("first_date"));
            row.put("lastDate", r.get("last_date"));
            row.put("createdAt", r.get("created_at"));
            row.put("updatedAt", r.get("updated_at"));
            rows.add(row);
        }
        return rows;
    }
}
