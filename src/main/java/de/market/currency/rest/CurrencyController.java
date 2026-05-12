package de.market.currency.rest;

import de.market.currency.rest.dto.CurrencyDto;
import de.market.currency.service.CurrencyService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.rest.AbstractCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController extends AbstractCrudController<CurrencyDto, Short> {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "currency_id", "NUMBER"),
            new ColumnMeta("isoCode", "ISO-Code", "iso_code", "TEXT"),
            new ColumnMeta("description", "Name", "description", "TEXT")
    );

    public CurrencyController(CurrencyService service) {
        super(service, COLUMNS);
    }
}
