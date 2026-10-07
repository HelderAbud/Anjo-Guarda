package com.anjoguarda.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, UUID> {

    Optional<MonthlyReport> findFirstByChildIdAndYearAndMonthOrderByVersionDesc(UUID childId, int year, int month);

    List<MonthlyReport> findByChildIdAndYearAndMonthOrderByVersionAsc(UUID childId, int year, int month);
}
