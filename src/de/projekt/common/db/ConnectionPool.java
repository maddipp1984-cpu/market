package de.projekt.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPool implements AutoCloseable {

    private final HikariDataSource dataSource;

    public ConnectionPool(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, 10);
    }

    public ConnectionPool(String jdbcUrl, String username, String password, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("ts-pool");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("prepareThreshold", "5");

        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
