package de.market.shared.dto;

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

            // Validate value length
            validateLength(c.getValue());
            validateLength(c.getValue2());

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
                    sql.append(col).append("::text ILIKE ? ESCAPE '\\'");
                    String escaped = c.getValue()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_");
                    params.add("%" + escaped + "%");
                    break;

                case "IN":
                    String[] parts = c.getValue().split(",");
                    List<String> inValues = new ArrayList<>();
                    boolean hasText = false;
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!isNumeric(trimmed)) hasText = true;
                        inValues.add(trimmed);
                    }
                    if (hasText) {
                        sql.append("UPPER(").append(col).append("::text) IN (");
                        for (int j = 0; j < inValues.size(); j++) {
                            if (j > 0) sql.append(", ");
                            sql.append("?");
                            params.add(inValues.get(j).toUpperCase());
                        }
                    } else {
                        sql.append(col).append(" IN (");
                        for (int j = 0; j < inValues.size(); j++) {
                            if (j > 0) sql.append(", ");
                            sql.append("?");
                            params.add(parseNumeric(inValues.get(j)));
                        }
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
                    params.add(parseNative(c.getValue()));
                    params.add(parseNative(c.getValue2()));
                    break;

                default: // =, !=, <, >, >=, <=
                    String rawValue = c.getValue();
                    boolean isEq = "=".equals(op) || "!=".equals(op);

                    if (!isNumeric(rawValue) && !isDateLike(rawValue)) {
                        // Text value → case-insensitive, ::text cast
                        sql.append("UPPER(").append(col).append("::text) ").append(op).append(" ?");
                        params.add(rawValue.toUpperCase());
                    } else if (isEq) {
                        // Equality with numeric/date → compare as text (safe for ALL column types)
                        sql.append(col).append("::text ").append(op).append(" ?");
                        params.add(rawValue);
                    } else {
                        // Ordering with numeric/date → bind native type
                        sql.append(col).append(" ").append(op).append(" ?");
                        params.add(parseNative(rawValue));
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
    private static final String NUMERIC_PATTERN = "-?\\d+(\\.\\d+)?";
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final String DATETIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$";

    private static boolean isNumeric(String value) {
        return value.matches(NUMERIC_PATTERN);
    }

    private static boolean isDateLike(String value) {
        return value.matches(DATE_PATTERN) || value.matches(DATETIME_PATTERN);
    }

    private static Number parseNumeric(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return Double.parseDouble(value);
        }
    }

    private static Object parseNative(String value) {
        if (isNumeric(value)) return parseNumeric(value);
        return value;
    }

    private static void validateLength(String value) {
        if (value != null && value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "Filterwert zu lang (max " + MAX_VALUE_LENGTH + " Zeichen)");
        }
    }
}
