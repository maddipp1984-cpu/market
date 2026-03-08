package de.market.shared.dto;

public class FilterCondition {
    private String sqlColumn;
    private String operator;   // =, !=, <, >, >=, <=, LIKE, IN, BETWEEN, IS NULL, IS NOT NULL
    private String value;
    private String value2;     // Zweiter Wert fuer BETWEEN
    private String conjunction; // AND, OR (null for last condition)

    public String getSqlColumn() { return sqlColumn; }
    public void setSqlColumn(String sqlColumn) { this.sqlColumn = sqlColumn; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getValue2() { return value2; }
    public void setValue2(String value2) { this.value2 = value2; }

    public String getConjunction() { return conjunction; }
    public void setConjunction(String conjunction) { this.conjunction = conjunction; }
}
