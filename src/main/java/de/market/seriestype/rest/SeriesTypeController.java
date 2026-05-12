package de.market.seriestype.rest;

import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.seriestype.service.SeriesTypeService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.rest.AbstractCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/series-types")
public class SeriesTypeController extends AbstractCrudController<SeriesTypeDto, Short> {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "series_type_id", "NUMBER"),
            new ColumnMeta("code", "Kuerzel", "code", "TEXT"),
            new ColumnMeta("name", "Name", "name", "TEXT"),
            new ColumnMeta("category", "Kategorie", "category", "NUMBER")
    );

    public SeriesTypeController(SeriesTypeService service) {
        super(service, COLUMNS);
    }
}
