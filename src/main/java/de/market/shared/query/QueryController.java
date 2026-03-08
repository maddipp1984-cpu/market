package de.market.shared.query;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/queries")
public class QueryController {

    private final QueryRegistry registry;

    public QueryController(QueryRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        registry.load();
        return ResponseEntity.ok(Map.of(
                "message", "Queries neu geladen",
                "count", registry.size()
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        Set<String> keys = registry.keys();
        return ResponseEntity.ok(Map.of(
                "count", keys.size(),
                "keys", keys
        ));
    }
}
