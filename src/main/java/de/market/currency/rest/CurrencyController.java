package de.market.currency.rest;

import de.market.currency.rest.dto.CurrencyDto;
import de.market.currency.service.CurrencyService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterCondition;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.TableResponse;
import org.jooq.Condition;
import org.jooq.Field;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;
import static org.jooq.impl.DSL.*;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "currency_id", "NUMBER"),
            new ColumnMeta("isoCode", "ISO-Code", "iso_code", "TEXT"),
            new ColumnMeta("description", "Name", "description", "TEXT")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            Condition condition = buildCondition(request.getConditions());
            data = service.findFiltered(condition);
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurrencyDto> getById(@PathVariable Short id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<CurrencyDto> create(@RequestBody CurrencyDto dto) {
        CurrencyDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurrencyDto> update(@PathVariable Short id, @RequestBody CurrencyDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // jOOQ condition builder (inline, no FilterQueryBuilder)
    // -------------------------------------------------------------------------

    private static final Set<String> VALID_OPERATORS = Set.of(
            "=", "!=", "<", ">", ">=", "<=", "LIKE", "IN", "BETWEEN", "IS NULL", "IS NOT NULL");

    private Condition buildCondition(List<FilterCondition> conditions) {
        List<Condition> parts = new ArrayList<>();
        List<String> conjunctions = new ArrayList<>();

        for (FilterCondition fc : conditions) {
            if (fc.getSqlColumn() == null || fc.getOperator() == null) {
                throw new IllegalArgumentException("sqlColumn und operator duerfen nicht null sein");
            }
            if (!ALLOWED_SQL_COLUMNS.contains(fc.getSqlColumn())) {
                throw new IllegalArgumentException(
                        "Unbekannte Spalte: " + fc.getSqlColumn() + ". Erlaubt: " + ALLOWED_SQL_COLUMNS);
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
            conjunctions.add(conj != null && conj.toUpperCase().equals("OR") ? "OR" : "AND");
        }

        // Combine all parts with their conjunctions
        Condition result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            String conj = conjunctions.get(i - 1);
            result = "OR".equals(conj) ? result.or(parts.get(i)) : result.and(parts.get(i));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Condition toJooqCondition(Field<Object> col, String op, FilterCondition fc) {
        switch (op) {
            case "IS NULL":
                return col.isNull();
            case "IS NOT NULL":
                return col.isNotNull();
            case "LIKE":
                validateLength(fc.getValue());
                String escaped = fc.getValue()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                return col.cast(String.class).likeIgnoreCase("%" + escaped + "%", '\\');
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
            case "BETWEEN":
                validateLength(fc.getValue());
                validateLength(fc.getValue2());
                if (fc.getValue() == null || fc.getValue().isEmpty()
                        || fc.getValue2() == null || fc.getValue2().isEmpty()) {
                    throw new IllegalArgumentException("BETWEEN erfordert zwei Werte (value und value2)");
                }
                return col.between(parseNative(fc.getValue()), parseNative(fc.getValue2()));
            default: { // =, !=, <, >, >=, <=
                validateLength(fc.getValue());
                String rawValue = fc.getValue();
                boolean isEq = "=".equals(op) || "!=".equals(op);
                if (!isNumeric(rawValue) && !isDateLike(rawValue)) {
                    // Text: case-insensitive
                    Field<String> upperCol = upper(col.cast(String.class));
                    Field<String> upperVal = val(rawValue.toUpperCase());
                    return condition("{0} " + op + " {1}", upperCol, upperVal);
                } else if (isEq) {
                    // Equality: compare as text
                    return col.cast(String.class).equal(rawValue);
                } else {
                    // Ordering: native type
                    return condition("{0} " + op + " {1}", col, val(parseNative(rawValue)));
                }
            }
        }
    }

    private static final int MAX_VALUE_LENGTH = 1000;
    private static final String NUMERIC_PATTERN = "-?\\d+(\\.\\d+)?";
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final String DATETIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$";

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
