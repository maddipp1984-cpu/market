package de.market.index.repository;

import de.market.index.model.IndexEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexJpaRepository extends JpaRepository<IndexEntity, Long> {

    boolean existsByObjectId(Long objectId);
}
