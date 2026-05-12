package de.market.businesspartner.service;

import de.market.businesspartner.model.BusinessPartner;
import de.market.businesspartner.model.ContactFunction;
import de.market.businesspartner.model.ContactPerson;
import de.market.businesspartner.repository.BusinessPartnerOverviewRepository;
import de.market.businesspartner.repository.BusinessPartnerRepository;
import de.market.businesspartner.rest.dto.BusinessPartnerDto;
import de.market.businesspartner.rest.dto.ContactPersonDto;
import de.market.shared.repository.AbstractOverviewRepository;
import de.market.shared.service.AbstractCrudService;
import org.jooq.Condition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class BusinessPartnerService extends AbstractCrudService<BusinessPartnerDto, BusinessPartner, Long> {

    private final BusinessPartnerRepository repository;
    private final BusinessPartnerOverviewRepository overviewRepository;

    public BusinessPartnerService(BusinessPartnerRepository repository, BusinessPartnerOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Override
    protected JpaRepository<BusinessPartner, Long> getRepository() { return repository; }

    @Override
    protected AbstractOverviewRepository getOverviewRepository() { return overviewRepository; }

    @Override
    protected String getEntityName() { return "Geschaeftspartner"; }

    @Override
    protected void prepareForCreate(BusinessPartner entity) { entity.setId(null); }

    @Override
    protected void checkUniqueOnCreate(BusinessPartnerDto dto) {
        if (repository.existsByShortName(dto.getShortName())) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }
    }

    @Override
    protected void checkUniqueOnUpdate(BusinessPartnerDto dto, Long id) {
        if (repository.existsByShortNameAndIdNot(dto.getShortName(), id)) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }
    }

    @Override
    protected void copyFieldsForUpdate(BusinessPartner existing, BusinessPartnerDto dto) {
        existing.setShortName(dto.getShortName());
        existing.setName(dto.getName());
        existing.setNotes(dto.getNotes());
        // Kontakte: via Override in update() — hier nur Stammdaten
    }

    // ---- Custom findFiltered (Step 5 vorbereitet) ----

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return overviewRepository.findFiltered(condition);
    }

    // ---- create/update/delete mit Nested-Child-Management ----

    @Override
    public BusinessPartnerDto create(BusinessPartnerDto dto) {
        validate(dto);
        checkUniqueOnCreate(dto);
        BusinessPartner entity = toEntity(dto);
        prepareForCreate(entity);
        return toDto(repository.save(entity));
    }

    @Override
    public BusinessPartnerDto update(Long id, BusinessPartnerDto dto) {
        validate(dto);
        BusinessPartner existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id));
        checkUniqueOnUpdate(dto, id);
        copyFieldsForUpdate(existing, dto);

        // Nested-Child-Management: Kontakte ersetzen
        existing.getContacts().clear();
        if (dto.getContacts() != null) {
            for (ContactPersonDto cpDto : dto.getContacts()) {
                existing.getContacts().add(toContactEntity(cpDto));
            }
        }

        return toDto(repository.save(existing));
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id);
        }
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Geschaeftspartner wird noch referenziert und kann nicht geloescht werden");
        }
    }

    @Override
    protected BusinessPartnerDto toDto(BusinessPartner entity) {
        BusinessPartnerDto dto = new BusinessPartnerDto();
        dto.setId(entity.getId());
        dto.setShortName(entity.getShortName());
        dto.setName(entity.getName());
        dto.setNotes(entity.getNotes());
        dto.setContacts(entity.getContacts().stream()
                .map(this::toContactDto)
                .toList());
        return dto;
    }

    private ContactPersonDto toContactDto(ContactPerson entity) {
        ContactPersonDto dto = new ContactPersonDto();
        dto.setId(entity.getId());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setStreet(entity.getStreet());
        dto.setZipCode(entity.getZipCode());
        dto.setCity(entity.getCity());
        dto.setFunctions(entity.getFunctions().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()));
        return dto;
    }

    @Override
    protected BusinessPartner toEntity(BusinessPartnerDto dto) {
        BusinessPartner entity = new BusinessPartner();
        entity.setShortName(dto.getShortName());
        entity.setName(dto.getName());
        entity.setNotes(dto.getNotes());
        if (dto.getContacts() != null) {
            for (ContactPersonDto cpDto : dto.getContacts()) {
                entity.getContacts().add(toContactEntity(cpDto));
            }
        }
        return entity;
    }

    @Override
    protected void validate(BusinessPartnerDto dto) {
        if (dto.getShortName() == null || dto.getShortName().isBlank()) {
            throw new IllegalArgumentException("Kurzbezeichnung ist ein Pflichtfeld");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
    }

    private ContactPerson toContactEntity(ContactPersonDto dto) {
        ContactPerson entity = new ContactPerson();
        entity.setId(dto.getId());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setStreet(dto.getStreet());
        entity.setZipCode(dto.getZipCode());
        entity.setCity(dto.getCity());
        if (dto.getFunctions() != null) {
            entity.setFunctions(dto.getFunctions().stream()
                    .map(f -> {
                        try {
                            return ContactFunction.valueOf(f);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Unbekannte Funktion: " + f);
                        }
                    })
                    .collect(Collectors.toSet()));
        }
        return entity;
    }
}
