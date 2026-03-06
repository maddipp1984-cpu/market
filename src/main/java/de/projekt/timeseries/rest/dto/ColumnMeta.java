package de.projekt.timeseries.rest.dto;

public class ColumnMeta {
    private String key;
    private String label;
    private String sqlColumn;
    private String type;

    public ColumnMeta(String key, String label, String sqlColumn) {
        this(key, label, sqlColumn, "TEXT");
    }

    public ColumnMeta(String key, String label, String sqlColumn, String type) {
        this.key = key;
        this.label = label;
        this.sqlColumn = sqlColumn;
        this.type = type;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getSqlColumn() { return sqlColumn; }
    public String getType() { return type; }
}
