package de.market.shared.dto;

import java.util.List;
import java.util.Map;

public class TableResponse {
    private List<ColumnMeta> columns;
    private List<Map<String, Object>> data;

    public TableResponse(List<ColumnMeta> columns, List<Map<String, Object>> data) {
        this.columns = columns;
        this.data = data;
    }

    public List<ColumnMeta> getColumns() { return columns; }
    public List<Map<String, Object>> getData() { return data; }
}
