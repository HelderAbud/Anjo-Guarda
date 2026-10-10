package com.anjoguarda.imports;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImportRepository extends JpaRepository<StoredImport, UUID> {}
