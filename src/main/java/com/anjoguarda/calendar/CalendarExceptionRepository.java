package com.anjoguarda.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarExceptionRepository extends JpaRepository<CalendarException, UUID> {

    Optional<CalendarException> findByChildIdAndDate(UUID childId, LocalDate date);

    List<CalendarException> findByChildIdAndDateBetween(UUID childId, LocalDate start, LocalDate end);
}
