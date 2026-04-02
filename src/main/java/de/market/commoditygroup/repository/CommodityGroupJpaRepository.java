package de.market.commoditygroup.repository;

import de.market.commoditygroup.model.CommodityGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommodityGroupJpaRepository extends JpaRepository<CommodityGroupEntity, Short> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Short id);
}
