package de.projekt.timeseries.rest.dto;

public class FilterCondition {
    private String sqlColumn;
    private String operator;   // =, !=, <, >, LIKE, IN
    private String value;
    private String conjunction; // AND, OR (null for last condition)

    public String getSqlColumn() { return sqlColumn; }
    public void setSqlColumn(String sqlColumn) { this.sqlColumn = sqlColumn; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getConjunction() { return conjunction; }
    public void setConjunction(String conjunction) { this.conjunction = conjunction; }
}
