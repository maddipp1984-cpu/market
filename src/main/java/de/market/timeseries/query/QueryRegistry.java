package de.market.timeseries.query;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QueryRegistry {

    private static final Logger log = LoggerFactory.getLogger(QueryRegistry.class);

    private final Map<String, String> queries = new ConcurrentHashMap<>();
    private final DataSource dataSource;

    public QueryRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void load() {
        Map<String, String> loaded = new ConcurrentHashMap<>();
        String sql = "SELECT query_key, sql_text FROM ts_query";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.put(rs.getString("query_key"), rs.getString("sql_text").strip());
            }
        } catch (SQLException e) {
            log.warn("QueryRegistry: Tabelle ts_query nicht verfuegbar. Keine Queries geladen.", e);
            return;
        }
        queries.clear();
        queries.putAll(loaded);
        log.info("QueryRegistry: {} Queries aus DB geladen", queries.size());
    }

    public String get(String key) {
        String sql = queries.get(key);
        if (sql == null) {
            throw new IllegalArgumentException("Query nicht gefunden: " + key);
        }
        return sql;
    }

    public boolean has(String key) {
        return queries.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(queries.keySet());
    }

    public int size() {
        return queries.size();
    }
}
