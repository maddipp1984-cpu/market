package de.projekt.timeseries.rest.dto;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FilterQueryBuilder {

    public static class WhereClause {
        private final String sql;
        private final List<Object> params;

        public WhereClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        public String getSql() { return sql; }
        public List<Object> getParams() { return params; }
    }

    private static final Set<String> VALID_OPERATORS = Set.of(
            "=", "!=", "<", ">", ">=", "<=", "LIKE", "IN", "BETWEEN", "IS NULL", "IS NOT NULL");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public static WhereClause build(List<FilterCondition> conditions, Set<String> allowedColumns) {
        if (conditions == null || conditions.isEmpty()) {
            return new WhereClause("1=1", List.of());
        }

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            FilterCondition c = conditions.get(i);

            // Validate column + operator not null
            if (c.getSqlColumn() == null || c.getOperator() == null) {
                throw new IllegalArgumentException("sqlColumn und operator duerfen nicht null sein");
            }
            if (!allowedColumns.contains(c.getSqlColumn())) {
                throw new IllegalArgumentException(
                        "Unbekannte Spalte: " + c.getSqlColumn()
                                + ". Erlaubt: " + allowedColumns);
            }

            // Validate operator
            String op = c.getOperator().toUpperCase();
            if (!VALID_OPERATORS.contains(op)) {
                throw new IllegalArgumentException(
                        "Unbekannter Operator: " + c.getOperator()
                                + ". Erlaubt: " + VALID_OPERATORS);
            }

            // Build clause for this condition
            String col = c.getSqlColumn();

            switch (op) {
                case "IS NULL":
                    sql.append(col).append(" IS NULL");
                    break;

                case "IS NOT NULL":
                    sql.append(col).append(" IS NOT NULL");
                    break;

                case "LIKE":
                    sql.append(col).append("::text ILIKE ?");
                    params.add("%" + c.getValue() + "%");
                    break;

                case "IN":
                    String[] parts = c.getValue().split(",");
                    List<Object> inValues = new ArrayList<>();
                    boolean hasText = false;
                    for (String part : parts) {
                        Object val = parseValue(part.trim());
                        if (val instanceof String) hasText = true;
                        inValues.add(val);
                    }
                    sql.append(hasText ? "UPPER(" + col + "::text)" : col).append(" IN (");
                    for (int j = 0; j < inValues.size(); j++) {
                        if (j > 0) sql.append(", ");
                        sql.append("?");
                        Object val = inValues.get(j);
                        params.add(val instanceof String ? ((String) val).toUpperCase() : val);
                    }
                    sql.append(")");
                    break;

                case "BETWEEN":
                    if (c.getValue() == null || c.getValue().isEmpty()
                            || c.getValue2() == null || c.getValue2().isEmpty()) {
                        throw new IllegalArgumentException(
                                "BETWEEN erfordert zwei Werte (value und value2)");
                    }
                    sql.append(col).append(" BETWEEN ? AND ?");
                    params.add(parseValue(c.getValue()));
                    params.add(parseValue(c.getValue2()));
                    break;

                default: // =, !=, <, >, >=, <=
                    Object parsed = parseValue(c.getValue());
                    if (parsed instanceof String) {
                        sql.append("UPPER(").append(col).append("::text) ").append(op).append(" ?");
                        params.add(((String) parsed).toUpperCase());
                    } else {
                        sql.append(col).append(" ").append(op).append(" ?");
                        params.add(parsed);
                    }
                    break;
            }

            // Conjunction between conditions (not after the last one)
            if (i < conditions.size() - 1) {
                String conj = c.getConjunction();
                if (conj == null || conj.isBlank()) {
                    conj = "AND";
                }
                conj = conj.toUpperCase();
                if (!"AND".equals(conj) && !"OR".equals(conj)) {
                    throw new IllegalArgumentException(
                            "Ungueltige Konjunktion: " + conj + ". Erlaubt: AND, OR");
                }
                sql.append(" ").append(conj).append(" ");
            }
        }

        return new WhereClause(sql.toString(), params);
    }

    private static final int MAX_VALUE_LENGTH = 1000;
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final String DATETIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$";

    private static Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "Filterwert zu lang (max " + MAX_VALUE_LENGTH + " Zeichen)");
        }
        // Datum-Parsing vor Zahlen-Parsing
        if (value.matches(DATE_PATTERN)) {
            try {
                return java.sql.Date.valueOf(LocalDate.parse(value));
            } catch (java.time.DateTimeException e) {
                throw new IllegalArgumentException("Ungueltiges Datum: " + value);
            }
        }
        if (value.matches(DATETIME_PATTERN)) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(value);
                return Timestamp.from(ldt.atZone(ZONE).toInstant());
            } catch (java.time.DateTimeException e) {
                throw new IllegalArgumentException("Ungueltiger Zeitstempel: " + value);
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e1) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e2) {
                return value;
            }
        }
    }
}
