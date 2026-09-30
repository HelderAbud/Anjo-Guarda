package com.anjoguarda.schedule;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, UUID> {

    Optional<ScheduleRule> findByPlanId(UUID planId);
}
