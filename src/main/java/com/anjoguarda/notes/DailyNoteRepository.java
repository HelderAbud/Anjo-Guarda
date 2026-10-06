package com.anjoguarda.notes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyNoteRepository extends JpaRepository<DailyNote, UUID> {

    Optional<DailyNote> findByIdAndDeletedAtIsNull(UUID id);

    Optional<DailyNote> findByChildIdAndDateAndDeletedAtIsNull(UUID childId, LocalDate date);

    List<DailyNote> findByChildIdAndDeletedAtIsNullAndDateBetweenOrderByDateAsc(
            UUID childId, LocalDate from, LocalDate to);
}
