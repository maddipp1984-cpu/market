package de.market.commoditygroup.service;

import de.market.commoditygroup.model.CommodityGroupEntity;
import de.market.commoditygroup.repository.CommodityGroupJpaRepository;
import de.market.commoditygroup.repository.CommodityGroupOverviewRepository;
import de.market.commoditygroup.rest.dto.CommodityGroupDto;
import de.market.shared.service.AbstractCrudService;
import org.jooq.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class CommodityGroupService extends AbstractCrudService<CommodityGroupDto, CommodityGroupEntity, Short> {

    private final CommodityGroupJpaRepository repository;
    private final CommodityGroupOverviewRepository overviewRepository;

    public CommodityGroupService(CommodityGroupJpaRepository repository, CommodityGroupOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() {
        return overviewRepository.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return overviewRepository.findFiltered(condition);
    }

    @Transactional(readOnly = true)
    public CommodityGroupDto findById(Short id) {
        CommodityGroupEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warengruppe nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public CommodityGroupDto create(CommodityGroupDto dto) {
        validate(dto);
        if (repository.existsByName(dto.getName())) {
            throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
        }
        CommodityGroupEntity entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public CommodityGroupDto update(Short id, CommodityGroupDto dto) {
        validate(dto);
        CommodityGroupEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warengruppe nicht gefunden: id=" + id));

        if (repository.existsByNameAndIdNot(dto.getName(), id)) {
            throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
        }

        existing.setName(dto.getName());
        return toDto(repository.save(existing));
    }

    public void delete(Short id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Warengruppe nicht gefunden: id=" + id);
        }
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Warengruppe wird noch referenziert und kann nicht geloescht werden");
        }
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
