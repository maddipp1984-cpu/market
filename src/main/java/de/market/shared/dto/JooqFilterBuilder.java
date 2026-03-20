package de.market.shared.dto;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

/**
 * Reusable jOOQ-based filter builder.
 * Replaces the old FilterQueryBuilder for controllers that use jOOQ.
 */
public class JooqFilterBuilder {

    private static final Set<String> VALID_OPERATORS = Set.of(
            "=", "!=", "<", ">", ">=", "<=", "LIKE", "IN", "BETWEEN", "IS NULL", "IS NOT NULL");

    private static final int MAX_VALUE_LENGTH = 1000;
    private static final String NUMERIC_PATTERN = "-?\\d+(\\.\\d+)?";
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final String DATETIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$";

    /**
     * Build a jOOQ Condition from a list of FilterConditions.
     *
     * @param conditions     list of filter conditions (must not be null or empty)
     * @param allowedColumns set of SQL column expressions that are permitted
     * @return combined jOOQ Condition
     */
    public static Condition build(List<FilterCondition> conditions, Set<String> allowedColumns) {
        List<Condition> parts = new ArrayList<>();
        List<String> conjunctions = new ArrayList<>();

        for (FilterCondition fc : conditions) {
            if (fc.getSqlColumn() == null || fc.getOperator() == null) {
                throw new IllegalArgumentException("sqlColumn und operator duerfen nicht null sein");
            }
            if (!allowedColumns.contains(fc.getSqlColumn())) {
                throw new IllegalArgumentException(
                        "Unbekannte Spalte: " + fc.getSqlColumn() + ". Erlaubt: " + allowedColumns);
            }
            String op = fc.getOperator().toUpperCase();
            if (!VALID_OPERATORS.contains(op)) {
                throw new IllegalArgumentException(
                        "Unbekannter Operator: " + fc.getOperator() + ". Erlaubt: " + VALID_OPERATORS);
            }

            Field<Object> col = field(fc.getSqlColumn());
            Condition cond = toJooqCondition(col, op, fc);
            parts.add(cond);

            String conj = fc.getConjunction();
            if (conj != null && !"AND".equalsIgnoreCase(conj) && !"OR".equalsIgnoreCase(conj)) {
                throw new IllegalArgumentException(
                        "Ungueltige Konjunktion: " + conj + ". Erlaubt: AND, OR");
            }
            conjunctions.add(conj != null && conj.toUpperCase().equals("OR") ? "OR" : "AND");
        }

        Condition result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            String conj = conjunctions.get(i - 1);
            result = "OR".equals(conj) ? result.or(parts.get(i)) : result.and(parts.get(i));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Condition toJooqCondition(Field<Object> col, String op, FilterCondition fc) {
        switch (op) {
            case "IS NULL":
                return col.isNull();
            case "IS NOT NULL":
                return col.isNotNull();
            case "LIKE": {
                validateLength(fc.getValue());
                String escaped = fc.getValue()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                return col.cast(String.class).likeIgnoreCase("%" + escaped + "%", '\\');
            }
            case "IN": {
                validateLength(fc.getValue());
                String[] rawParts = fc.getValue().split(",");
                List<Object> inVals = new ArrayList<>();
                boolean hasText = false;
                for (String p : rawParts) {
                    String t = p.trim();
                    if (!isNumeric(t)) hasText = true;
                    inVals.add(t);
                }
                if (hasText) {
                    List<String> upper = inVals.stream()
                            .map(v -> v.toString().toUpperCase())
                            .collect(Collectors.toList());
                    return upper(col.cast(String.class)).in(upper);
                } else {
                    List<Object> nums = inVals.stream()
                            .map(v -> (Object) parseNumeric(v.toString()))
                            .collect(Collectors.toList());
                    return col.in(nums);
                }
            }
            case "BETWEEN": {
                validateLength(fc.getValue());
                validateLength(fc.getValue2());
                if (fc.getValue() == null || fc.getValue().isEmpty()
                        || fc.getValue2() == null || fc.getValue2().isEmpty()) {
                    throw new IllegalArgumentException("BETWEEN erfordert zwei Werte (value und value2)");
                }
                return col.between(parseNative(fc.getValue()), parseNative(fc.getValue2()));
            }
            default: { // =, !=, <, >, >=, <=
                validateLength(fc.getValue());
                String rawValue = fc.getValue();
                boolean isEq = "=".equals(op) || "!=".equals(op);
                if (!isNumeric(rawValue) && !isDateLike(rawValue)) {
                    // Text: case-insensitive comparison
                    Field<String> upperCol = upper(col.cast(String.class));
                    Field<String> upperVal = val(rawValue.toUpperCase());
                    return condition("{0} " + op + " {1}", upperCol, upperVal);
                } else if (isEq) {
                    // Equality with numeric/date: compare as text (safe for all column types)
                    return col.cast(String.class).equal(rawValue);
                } else {
                    // Ordering: use native type
                    return condition("{0} " + op + " {1}", col, val(parseNative(rawValue)));
                }
            }
        }
    }

    private static boolean isNumeric(String value) {
        return value != null && value.matches(NUMERIC_PATTERN);
    }

    private static boolean isDateLike(String value) {
        return value != null && (value.matches(DATE_PATTERN) || value.matches(DATETIME_PATTERN));
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
            throw new IllegalArgumentException("Filterwert zu lang (max " + MAX_VALUE_LENGTH + " Zeichen)");
        }
    }
}
