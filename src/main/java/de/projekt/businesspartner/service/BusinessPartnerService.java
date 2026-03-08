package de.projekt.businesspartner.service;

import de.projekt.businesspartner.model.BusinessPartner;
import de.projekt.businesspartner.model.ContactFunction;
import de.projekt.businesspartner.model.ContactPerson;
import de.projekt.businesspartner.repository.BusinessPartnerOverviewRepository;
import de.projekt.businesspartner.repository.BusinessPartnerRepository;
import de.projekt.businesspartner.rest.dto.BusinessPartnerDto;
import de.projekt.businesspartner.rest.dto.ContactPersonDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class BusinessPartnerService {

    private final BusinessPartnerRepository repository;
    private final BusinessPartnerOverviewRepository overviewRepository;

    public BusinessPartnerService(BusinessPartnerRepository repository, BusinessPartnerOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        return overviewRepository.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public BusinessPartnerDto findById(Long id) {
        BusinessPartner entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public BusinessPartnerDto create(BusinessPartnerDto dto) {
        validateRequired(dto);
        if (repository.existsByShortName(dto.getShortName())) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }
        BusinessPartner entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public BusinessPartnerDto update(Long id, BusinessPartnerDto dto) {
        validateRequired(dto);
        BusinessPartner existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id));

        if (repository.existsByShortNameAndIdNot(dto.getShortName(), id)) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }

        existing.setShortName(dto.getShortName());
        existing.setName(dto.getName());
        existing.setNotes(dto.getNotes());

        existing.getContacts().clear();
        if (dto.getContacts() != null) {
            for (ContactPersonDto cpDto : dto.getContacts()) {
                existing.getContacts().add(toContactEntity(cpDto));
            }
        }

        return toDto(repository.save(existing));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id);
        }
        repository.deleteById(id);
    }

    private BusinessPartnerDto toDto(BusinessPartner entity) {
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

    private BusinessPartner toEntity(BusinessPartnerDto dto) {
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

    private void validateRequired(BusinessPartnerDto dto) {
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
