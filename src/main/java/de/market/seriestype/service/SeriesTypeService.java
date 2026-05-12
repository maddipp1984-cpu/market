package de.market.seriestype.service;

import de.market.seriestype.model.SeriesCategory;
import de.market.seriestype.model.SeriesTypeEntity;
import de.market.seriestype.repository.SeriesTypeJpaRepository;
import de.market.seriestype.repository.SeriesTypeOverviewRepository;
import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.shared.repository.AbstractOverviewRepository;
import de.market.shared.service.AbstractCrudService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SeriesTypeService extends AbstractCrudService<SeriesTypeDto, SeriesTypeEntity, Short> {

    private final SeriesTypeJpaRepository repository;
    private final SeriesTypeOverviewRepository overviewRepository;

    public SeriesTypeService(SeriesTypeJpaRepository repository, SeriesTypeOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Override
    protected JpaRepository<SeriesTypeEntity, Short> getRepository() { return repository; }

    @Override
    protected AbstractOverviewRepository getOverviewRepository() { return overviewRepository; }

    @Override
    protected String getEntityName() { return "Reihenart"; }

    @Override
    protected void prepareForCreate(SeriesTypeEntity entity) { entity.setId(null); }

    @Override
    protected void checkUniqueOnCreate(SeriesTypeDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }
    }

    @Override
    protected void checkUniqueOnUpdate(SeriesTypeDto dto, Short id) {
        if (repository.existsByCodeAndIdNot(dto.getCode(), id)) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }
    }

    @Override
    protected void copyFieldsForUpdate(SeriesTypeEntity existing, SeriesTypeDto dto) {
        existing.setCode(dto.getCode());
        existing.setName(dto.getName());
        existing.setCategory((short) dto.getCategory());
    }

    @Override
    protected SeriesTypeDto toDto(SeriesTypeEntity entity) {
        SeriesTypeDto dto = new SeriesTypeDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setCategory(entity.getCategory());
        return dto;
    }

    @Override
    protected SeriesTypeEntity toEntity(SeriesTypeDto dto) {
        SeriesTypeEntity entity = new SeriesTypeEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setCategory((short) dto.getCategory());
        return entity;
    }

    @Override
    protected void validate(SeriesTypeDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Kuerzel ist ein Pflichtfeld");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
        SeriesCategory.fromCode(dto.getCategory());
    }
}
