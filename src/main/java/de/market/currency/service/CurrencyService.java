package de.market.currency.service;

import de.market.currency.model.CurrencyEntity;
import de.market.currency.repository.CurrencyJpaRepository;
import de.market.currency.repository.CurrencyOverviewRepository;
import de.market.currency.rest.dto.CurrencyDto;
import de.market.shared.repository.AbstractOverviewRepository;
import de.market.shared.service.AbstractCrudService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CurrencyService extends AbstractCrudService<CurrencyDto, CurrencyEntity, Short> {

    private final CurrencyJpaRepository repository;
    private final CurrencyOverviewRepository overviewRepository;

    public CurrencyService(CurrencyJpaRepository repository, CurrencyOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Override
    protected JpaRepository<CurrencyEntity, Short> getRepository() { return repository; }

    @Override
    protected AbstractOverviewRepository getOverviewRepository() { return overviewRepository; }

    @Override
    protected String getEntityName() { return "Waehrung"; }

    @Override
    protected void prepareForCreate(CurrencyEntity entity) { entity.setId(null); }

    @Override
    protected void checkUniqueOnCreate(CurrencyDto dto) {
        if (repository.existsByIsoCode(dto.getIsoCode())) {
            throw new IllegalStateException("ISO-Code bereits vergeben: " + dto.getIsoCode());
        }
    }

    @Override
    protected void checkUniqueOnUpdate(CurrencyDto dto, Short id) {
        if (repository.existsByIsoCodeAndIdNot(dto.getIsoCode(), id)) {
            throw new IllegalStateException("ISO-Code bereits vergeben: " + dto.getIsoCode());
        }
    }

    @Override
    protected void copyFieldsForUpdate(CurrencyEntity existing, CurrencyDto dto) {
        existing.setIsoCode(dto.getIsoCode());
        existing.setDescription(dto.getDescription());
    }

    @Override
    protected CurrencyDto toDto(CurrencyEntity entity) {
        CurrencyDto dto = new CurrencyDto();
        dto.setId(entity.getId());
        dto.setIsoCode(entity.getIsoCode());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    @Override
    protected CurrencyEntity toEntity(CurrencyDto dto) {
        CurrencyEntity entity = new CurrencyEntity();
        entity.setIsoCode(dto.getIsoCode());
        entity.setDescription(dto.getDescription());
        return entity;
    }

    @Override
    protected void validate(CurrencyDto dto) {
        if (dto.getIsoCode() == null || dto.getIsoCode().isBlank()) {
            throw new IllegalArgumentException("ISO-Code ist ein Pflichtfeld");
        }
        if (dto.getIsoCode().length() != 3) {
            throw new IllegalArgumentException("ISO-Code muss exakt 3 Zeichen lang sein");
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
    }
}
