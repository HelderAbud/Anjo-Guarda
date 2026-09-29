package com.anjoguarda.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family_access")
public class FamilyAccess {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected FamilyAccess() {}

    public FamilyAccess(UUID id, UUID familyId, UUID userId, String role, Instant acceptedAt) {
        this.id = id;
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        this.acceptedAt = acceptedAt;
    }

    public UUID getFamilyId() {
        return familyId;
    }
}
