package com.anjoguarda.family;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

    List<Guardian> findByChildId(UUID childId);
}
