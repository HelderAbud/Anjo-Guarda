package com.anjoguarda.family;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChildService {

    private final FamilyRepository families;
    private final FamilyAccessRepository access;
    private final ChildRepository children;
    private final GuardianRepository guardians;

    public ChildService(
            FamilyRepository families,
            FamilyAccessRepository access,
            ChildRepository children,
            GuardianRepository guardians) {
        this.families = families;
        this.access = access;
        this.children = children;
        this.guardians = guardians;
    }

    @Transactional
    public ChildResponse register(UUID userId, RegisterChildRequest request) {
        Instant now = Instant.now();
        UUID familyId = access.findFirstByUserId(userId)
                .map(FamilyAccess::getFamilyId)
                .orElseGet(() -> createFamily(userId, request.name(), now));

        Child child = children.save(new Child(UUID.randomUUID(), familyId, request.name(), request.birthDate(), now));
        List<Guardian> saved = request.guardians().stream()
                .map(guardian -> guardians.save(new Guardian(
                        UUID.randomUUID(), child.getId(), guardian.name(), guardian.relationship())))
                .toList();
        return toResponse(child, saved);
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> list(UUID userId) {
        return access.findFirstByUserId(userId)
                .map(entry -> children.findByFamilyIdAndActiveTrue(entry.getFamilyId()).stream()
                        .map(child -> toResponse(child, guardians.findByChildId(child.getId())))
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public ChildResponse get(UUID userId, UUID childId) {
        Child child = children.findById(childId)
                .filter(found -> access.existsByFamilyIdAndUserId(found.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
        return toResponse(child, guardians.findByChildId(child.getId()));
    }

    private UUID createFamily(UUID userId, String childName, Instant now) {
        Family family = families.save(new Family(UUID.randomUUID(), childName, now));
        access.save(new FamilyAccess(UUID.randomUUID(), family.getId(), userId, "OWNER", now));
        return family.getId();
    }

    private ChildResponse toResponse(Child child, List<Guardian> saved) {
        return new ChildResponse(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                saved.stream()
                        .map(guardian -> new ChildResponse.GuardianResponse(
                                guardian.getId(), guardian.getName(), guardian.getRelationship(), guardian.getUserId()))
                        .toList());
    }
}
