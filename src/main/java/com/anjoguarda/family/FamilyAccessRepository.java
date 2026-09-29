package com.anjoguarda.family;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyAccessRepository extends JpaRepository<FamilyAccess, UUID> {

    Optional<FamilyAccess> findFirstByUserId(UUID userId);

    boolean existsByFamilyIdAndUserId(UUID familyId, UUID userId);
}
