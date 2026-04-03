package de.market.index.service;

import de.market.index.model.IndexEntity;
import de.market.index.repository.IndexJpaRepository;
import de.market.index.repository.IndexOverviewRepository;
import de.market.index.rest.dto.IndexDto;
import de.market.shared.service.AbstractCrudService;
import de.market.timeseries.model.ObjectType;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.Unit;
import de.market.timeseries.model.Currency;
import de.market.timeseries.repository.HeaderRepository;
import de.market.timeseries.repository.ObjectRepository;
import de.market.timeseries.repository.TimeSeriesRepository;
import de.market.timeseries.model.TsObject;

import org.jooq.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class IndexService extends AbstractCrudService<IndexDto, IndexEntity, Long> {

    private final IndexJpaRepository indexRepo;
    private final IndexOverviewRepository overviewRepo;
    private final ObjectRepository objectRepo;
    private final HeaderRepository headerRepo;
    private final TimeSeriesRepository tsRepo;

    public IndexService(IndexJpaRepository indexRepo,
                        IndexOverviewRepository overviewRepo,
                        ObjectRepository objectRepo,
                        HeaderRepository headerRepo,
                        TimeSeriesRepository tsRepo) {
        this.indexRepo = indexRepo;
        this.overviewRepo = overviewRepo;
        this.objectRepo = objectRepo;
        this.headerRepo = headerRepo;
        this.tsRepo = tsRepo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() {
        return overviewRepo.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return overviewRepo.findFiltered(condition);
    }

    @Transactional(readOnly = true)
    public IndexDto findById(Long id) {
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public IndexDto create(IndexDto dto) {
        validate(dto);

        if (objectRepo.findByKey(dto.getName()).isPresent()) {
            throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
        }

        // 1. ts_object erstellen
        TsObject obj = new TsObject(ObjectType.INDEX, dto.getName(), dto.getDescription());
        long objectId = objectRepo.create(obj);

        // 2. ts_index erstellen
        IndexEntity entity = new IndexEntity();
        entity.setObjectId(objectId);
        entity = indexRepo.save(entity);

        // 3. ts_header erstellen
        TimeDimension dim = TimeDimension.fromCode(dto.getTimeDim());
        Unit unit = dto.getUnitId() != null ? Unit.fromCode(dto.getUnitId()) : Unit.NONE;
        Currency currency = dto.getCurrencyId() != null ? Currency.fromCode(dto.getCurrencyId()) : null;

        String tsKey = "IDX_" + dto.getName() + "_" + dim.name();
        TimeSeriesHeader header = new TimeSeriesHeader(tsKey, dim, unit, currency);
        header.setObjectId(objectId);
        header.setDescription(dto.getDescription());
        headerRepo.create(header);

        return toDto(entity);
    }

    public IndexDto update(Long id, IndexDto dto) {
        validate(dto);
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));

        TsObject obj = objectRepo.findById(entity.getObjectId())
                .orElseThrow(() -> new IllegalStateException("Objekt nicht gefunden: objectId=" + entity.getObjectId()));

        if (!obj.getObjectKey().equals(dto.getName())) {
            if (objectRepo.findByKey(dto.getName()).isPresent()) {
                throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
            }
        }

        obj.setObjectKey(dto.getName());
        obj.setDescription(dto.getDescription());
        objectRepo.update(obj);

        return toDto(entity);
    }

    public void delete(Long id) {
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));

        List<TimeSeriesHeader> headers = headerRepo.findByObjectId(entity.getObjectId());
        for (TimeSeriesHeader h : headers) {
            tsRepo.delete(h.getTsId(), h.getTimeDimension());
            headerRepo.delete(h.getTsId());
        }

        // ts_object löschen — ts_index wird per CASCADE mitgelöscht
        objectRepo.delete(entity.getObjectId());
    }

    @Override
    protected void validate(IndexDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
        if (dto.getTimeDim() == null) {
            throw new IllegalArgumentException("Zeitdimension ist ein Pflichtfeld");
        }
        try {
            TimeDimension.fromCode(dto.getTimeDim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige Zeitdimension: " + dto.getTimeDim());
        }
        if (dto.getUnitId() == null && dto.getCurrencyId() == null) {
            throw new IllegalArgumentException("Einheit oder Waehrung muss gesetzt sein");
        }
    }

    @Override
    protected IndexDto toDto(IndexEntity entity) {
        IndexDto dto = new IndexDto();
        dto.setId(entity.getId());

        TsObject obj = objectRepo.findById(entity.getObjectId()).orElse(null);
        if (obj != null) {
            dto.setName(obj.getObjectKey());
            dto.setDescription(obj.getDescription());
        }

        List<TimeSeriesHeader> headers = headerRepo.findByObjectId(entity.getObjectId());
        if (!headers.isEmpty()) {
            TimeSeriesHeader h = headers.get(0);
            dto.setTimeDim(h.getTimeDimension().getCode());
            dto.setUnitId((short) h.getUnit().getCode());
            dto.setCurrencyId(h.getCurrency() != null ? (short) h.getCurrency().getCode() : null);
            dto.setTsId(h.getTsId());
        }

        return dto;
    }

    @Override
    protected IndexEntity toEntity(IndexDto dto) {
        throw new UnsupportedOperationException("Use create() or update() instead");
    }
}
