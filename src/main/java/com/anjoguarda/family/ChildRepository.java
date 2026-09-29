package com.anjoguarda.family;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, UUID> {

    List<Child> findByFamilyIdAndActiveTrue(UUID familyId);
}
