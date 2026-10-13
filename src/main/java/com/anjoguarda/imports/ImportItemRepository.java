package com.anjoguarda.imports;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportItemRepository extends JpaRepository<ImportItem, UUID> {

    List<ImportItem> findByImportIdOrderByPositionAsc(UUID importId);
}
