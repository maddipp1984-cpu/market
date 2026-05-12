package de.market.index.rest;

import de.market.index.rest.dto.IndexDto;
import de.market.index.service.IndexService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.rest.AbstractCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/indices")
public class IndexController extends AbstractCrudController<IndexDto, Long> {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "index_id", "NUMBER"),
            new ColumnMeta("name", "Name", "object_key", "TEXT"),
            new ColumnMeta("description", "Beschreibung", "description", "TEXT"),
            new ColumnMeta("timeDim", "Zeitdimension", "time_dim", "NUMBER"),
            new ColumnMeta("unit", "Einheit", "symbol", "TEXT"),
            new ColumnMeta("currency", "Waehrung", "iso_code", "TEXT"),
            new ColumnMeta("firstDate", "Erster Wert", "first_date", "DATE"),
            new ColumnMeta("lastDate", "Letzter Wert", "last_date", "DATE"),
            new ColumnMeta("createdAt", "Erstellt", "created_at", "DATE")
    );

    public IndexController(IndexService service) {
        super(service, COLUMNS);
    }
}
