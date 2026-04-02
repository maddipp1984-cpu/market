package de.market.seriestype.service;

import de.market.seriestype.model.SeriesCategory;
import de.market.seriestype.model.SeriesTypeEntity;
import de.market.seriestype.repository.SeriesTypeJpaRepository;
import de.market.seriestype.repository.SeriesTypeOverviewRepository;
import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.shared.service.AbstractCrudService;
import org.jooq.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SeriesTypeService extends AbstractCrudService<SeriesTypeDto, SeriesTypeEntity, Short> {

    private final SeriesTypeJpaRepository repository;
    private final SeriesTypeOverviewRepository overviewRepository;

    public SeriesTypeService(SeriesTypeJpaRepository repository, SeriesTypeOverviewRepository overviewRepository) {
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
    public SeriesTypeDto findById(Short id) {
        SeriesTypeEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reihenart nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public SeriesTypeDto create(SeriesTypeDto dto) {
        validate(dto);
        if (repository.existsByCode(dto.getCode())) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }
        SeriesTypeEntity entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public SeriesTypeDto update(Short id, SeriesTypeDto dto) {
        validate(dto);
        SeriesTypeEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reihenart nicht gefunden: id=" + id));

        if (repository.existsByCodeAndIdNot(dto.getCode(), id)) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }

        existing.setCode(dto.getCode());
        existing.setName(dto.getName());
        existing.setCategory((short) dto.getCategory());
        return toDto(repository.save(existing));
    }

    public void delete(Short id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Reihenart nicht gefunden: id=" + id);
        }
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Reihenart wird noch von Zeitreihen referenziert und kann nicht geloescht werden");
        }
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
