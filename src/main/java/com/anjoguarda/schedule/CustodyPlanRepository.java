package com.anjoguarda.schedule;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyPlanRepository extends JpaRepository<CustodyPlan, UUID> {

    Optional<CustodyPlan> findByChildIdAndActiveTrue(UUID childId);

    boolean existsByChildIdAndActiveTrue(UUID childId);
}
