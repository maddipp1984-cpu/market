package de.market.currency.repository;

import de.market.currency.model.CurrencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyJpaRepository extends JpaRepository<CurrencyEntity, Short> {

    boolean existsByIsoCode(String isoCode);

    boolean existsByIsoCodeAndIdNot(String isoCode, Short id);
}
