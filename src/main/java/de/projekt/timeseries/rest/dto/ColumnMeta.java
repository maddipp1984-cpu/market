package de.projekt.timeseries.rest.dto;

public class ColumnMeta {
    private String key;
    private String label;
    private String sqlColumn;

    public ColumnMeta(String key, String label, String sqlColumn) {
        this.key = key;
        this.label = label;
        this.sqlColumn = sqlColumn;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getSqlColumn() { return sqlColumn; }
}
