package com.anjoguarda.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarConfirmationRepository extends JpaRepository<CalendarConfirmation, UUID> {

    Optional<CalendarConfirmation> findByChildIdAndDate(UUID childId, LocalDate date);

    List<CalendarConfirmation> findByChildIdAndDateBetween(UUID childId, LocalDate start, LocalDate end);
}
