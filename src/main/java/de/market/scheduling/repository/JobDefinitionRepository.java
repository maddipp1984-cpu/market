package de.market.scheduling.repository;

import de.market.scheduling.model.JobDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobDefinitionRepository extends JpaRepository<JobDefinitionEntity, Integer> {
    Optional<JobDefinitionEntity> findByJobKey(String jobKey);
}
