package de.market.businesspartner.repository;

import de.market.businesspartner.model.BusinessPartner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {
    boolean existsByShortName(String shortName);
    boolean existsByShortNameAndIdNot(String shortName, Long id);
}
