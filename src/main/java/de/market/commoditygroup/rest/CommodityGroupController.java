package de.market.commoditygroup.rest;

import de.market.commoditygroup.rest.dto.CommodityGroupDto;
import de.market.commoditygroup.service.CommodityGroupService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.rest.AbstractCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commodity-groups")
public class CommodityGroupController extends AbstractCrudController<CommodityGroupDto, Short> {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "commodity_group_id", "NUMBER"),
            new ColumnMeta("name", "Name", "name", "TEXT")
    );

    public CommodityGroupController(CommodityGroupService service) {
        super(service, COLUMNS);
    }
}
