package com.anjoguarda.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "guardians")
public class Guardian {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String relationship;

    @Column(name = "user_id")
    private UUID userId;

    protected Guardian() {}

    public Guardian(UUID id, UUID childId, String name, String relationship) {
        this.id = id;
        this.childId = childId;
        this.name = name;
        this.relationship = relationship;
        this.userId = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public String getName() {
        return name;
    }

    public String getRelationship() {
        return relationship;
    }

    public UUID getUserId() {
        return userId;
    }
}
