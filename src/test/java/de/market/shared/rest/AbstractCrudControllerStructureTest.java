package de.market.shared.rest;

import de.market.businesspartner.rest.BusinessPartnerController;
import de.market.commoditygroup.rest.CommodityGroupController;
import de.market.currency.rest.CurrencyController;
import de.market.index.rest.IndexController;
import de.market.seriestype.rest.SeriesTypeController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractCrudControllerStructureTest {

    @Test
    void allControllersShouldExtendAbstractCrudController() {
        assertThat(CurrencyController.class.getSuperclass())
                .isEqualTo(AbstractCrudController.class);
        assertThat(SeriesTypeController.class.getSuperclass())
                .isEqualTo(AbstractCrudController.class);
        assertThat(CommodityGroupController.class.getSuperclass())
                .isEqualTo(AbstractCrudController.class);
        assertThat(IndexController.class.getSuperclass())
                .isEqualTo(AbstractCrudController.class);
        assertThat(BusinessPartnerController.class.getSuperclass())
                .isEqualTo(AbstractCrudController.class);
    }
}
