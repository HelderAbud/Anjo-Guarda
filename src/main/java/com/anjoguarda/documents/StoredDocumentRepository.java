package com.anjoguarda.documents;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    List<StoredDocument> findByChildIdOrderByFilenameAscIdAsc(UUID childId);
}
