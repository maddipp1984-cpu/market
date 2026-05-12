package de.market.businesspartner.rest;

import de.market.businesspartner.rest.dto.BusinessPartnerDto;
import de.market.businesspartner.service.BusinessPartnerService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.rest.AbstractCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business-partners")
public class BusinessPartnerController extends AbstractCrudController<BusinessPartnerDto, Long> {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "bp.id", "NUMBER"),
            new ColumnMeta("shortName", "Kurzbezeichnung", "bp.short_name", "TEXT"),
            new ColumnMeta("name", "Name", "bp.name", "TEXT"),
            new ColumnMeta("systemRank", "Systemfirma", "bp.system_rank", "NUMBER")
    );

    public BusinessPartnerController(BusinessPartnerService service) {
        super(service, COLUMNS);
    }
}
