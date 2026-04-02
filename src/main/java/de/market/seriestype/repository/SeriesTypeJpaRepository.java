package de.market.seriestype.repository;

import de.market.seriestype.model.SeriesTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesTypeJpaRepository extends JpaRepository<SeriesTypeEntity, Short> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Short id);
}
