package de.projekt.timeseries.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class QueryLoader {

    private static final Logger log = LoggerFactory.getLogger(QueryLoader.class);

    private final DataSource dataSource;
    private final QueryRegistry registry;

    public QueryLoader(DataSource dataSource, QueryRegistry registry) {
        this.dataSource = dataSource;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadQueriesFromXml() {
        int count = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:queries/*.xml");

            try (Connection conn = dataSource.getConnection()) {
                for (Resource resource : resources) {
                    count += processXml(conn, resource);
                }
            }
        } catch (Exception e) {
            log.error("Fehler beim Laden der Query-XMLs", e);
            return;
        }

        log.info("QueryLoader: {} Queries aus XML in DB geschrieben", count);
        registry.load();
    }

    private int processXml(Connection conn, Resource resource) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resource.getInputStream());

        NodeList nodes = doc.getElementsByTagName("query");
        int count = 0;

        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String key = el.getAttribute("key");
            String name = el.getAttribute("name");
            String sqlText = el.getTextContent().strip();

            upsert(conn, key, name, sqlText);
            count++;
        }
        return count;
    }

    private void upsert(Connection conn, String key, String name, String sqlText) throws SQLException {
        String sql = """
                INSERT INTO ts_query (query_key, name, sql_text, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (query_key)
                DO UPDATE SET name = EXCLUDED.name,
                              sql_text = EXCLUDED.sql_text,
                              updated_at = NOW()
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, name);
            ps.setString(3, sqlText);
            ps.executeUpdate();
        }
    }
}
