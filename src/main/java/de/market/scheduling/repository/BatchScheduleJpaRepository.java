package de.market.scheduling.repository;

import de.market.scheduling.model.BatchScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchScheduleJpaRepository extends JpaRepository<BatchScheduleEntity, Integer> {
    List<BatchScheduleEntity> findByJobKey(String jobKey);
}
