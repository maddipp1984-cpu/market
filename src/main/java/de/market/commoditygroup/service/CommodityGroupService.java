package de.market.commoditygroup.service;

import de.market.commoditygroup.model.CommodityGroupEntity;
import de.market.commoditygroup.repository.CommodityGroupJpaRepository;
import de.market.commoditygroup.repository.CommodityGroupOverviewRepository;
import de.market.commoditygroup.rest.dto.CommodityGroupDto;
import de.market.shared.repository.AbstractOverviewRepository;
import de.market.shared.service.AbstractCrudService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommodityGroupService extends AbstractCrudService<CommodityGroupDto, CommodityGroupEntity, Short> {

    private final CommodityGroupJpaRepository repository;
    private final CommodityGroupOverviewRepository overviewRepository;

    public CommodityGroupService(CommodityGroupJpaRepository repository, CommodityGroupOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Override
    protected JpaRepository<CommodityGroupEntity, Short> getRepository() { return repository; }

    @Override
    protected AbstractOverviewRepository getOverviewRepository() { return overviewRepository; }

    @Override
    protected String getEntityName() { return "Warengruppe"; }

    @Override
    protected void prepareForCreate(CommodityGroupEntity entity) { entity.setId(null); }

    @Override
    protected void checkUniqueOnCreate(CommodityGroupDto dto) {
        if (repository.existsByName(dto.getName())) {
            throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
        }
    }

    @Override
    protected void checkUniqueOnUpdate(CommodityGroupDto dto, Short id) {
        if (repository.existsByNameAndIdNot(dto.getName(), id)) {
            throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
        }
    }

    @Override
    protected void copyFieldsForUpdate(CommodityGroupEntity existing, CommodityGroupDto dto) {
        existing.setName(dto.getName());
    }

    @Override
    protected CommodityGroupDto toDto(CommodityGroupEntity entity) {
        CommodityGroupDto dto = new CommodityGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        return dto;
    }

    @Override
    protected CommodityGroupEntity toEntity(CommodityGroupDto dto) {
        CommodityGroupEntity entity = new CommodityGroupEntity();
        entity.setName(dto.getName());
        return entity;
    }

    @Override
    protected void validate(CommodityGroupDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
    }
}
