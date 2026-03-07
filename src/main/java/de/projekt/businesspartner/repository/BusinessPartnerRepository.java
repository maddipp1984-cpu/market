package de.projekt.businesspartner.repository;

import de.projekt.businesspartner.model.BusinessPartner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {
    boolean existsByShortName(String shortName);
    boolean existsByShortNameAndIdNot(String shortName, Long id);
}
