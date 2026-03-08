package de.market.currency.service;

import de.market.currency.model.CurrencyEntity;
import de.market.currency.repository.CurrencyJpaRepository;
import de.market.currency.repository.CurrencyOverviewRepository;
import de.market.currency.rest.dto.CurrencyDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class CurrencyService {

    private final CurrencyJpaRepository repository;
    private final CurrencyOverviewRepository overviewRepository;

    public CurrencyService(CurrencyJpaRepository repository, CurrencyOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        return overviewRepository.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(String whereSql, List<Object> params) throws SQLException {
        return overviewRepository.findFiltered(whereSql, params);
    }

    @Transactional(readOnly = true)
    public CurrencyDto findById(Short id) {
        CurrencyEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Waehrung nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public CurrencyDto create(CurrencyDto dto) {
        validateRequired(dto);
        if (repository.existsByIsoCode(dto.getIsoCode())) {
            throw new IllegalStateException("ISO-Code bereits vergeben: " + dto.getIsoCode());
        }
        CurrencyEntity entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public CurrencyDto update(Short id, CurrencyDto dto) {
        validateRequired(dto);
        CurrencyEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Waehrung nicht gefunden: id=" + id));

        if (repository.existsByIsoCodeAndIdNot(dto.getIsoCode(), id)) {
            throw new IllegalStateException("ISO-Code bereits vergeben: " + dto.getIsoCode());
        }

        existing.setIsoCode(dto.getIsoCode());
        existing.setDescription(dto.getDescription());
        return toDto(repository.save(existing));
    }

    public void delete(Short id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Waehrung nicht gefunden: id=" + id);
        }
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Waehrung wird noch von Zeitreihen referenziert und kann nicht geloescht werden");
        }
    }

    private CurrencyDto toDto(CurrencyEntity entity) {
        CurrencyDto dto = new CurrencyDto();
        dto.setId(entity.getId());
        dto.setIsoCode(entity.getIsoCode());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    private CurrencyEntity toEntity(CurrencyDto dto) {
        CurrencyEntity entity = new CurrencyEntity();
        entity.setIsoCode(dto.getIsoCode());
        entity.setDescription(dto.getDescription());
        return entity;
    }

    private void validateRequired(CurrencyDto dto) {
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
