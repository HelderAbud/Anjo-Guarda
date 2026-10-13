package com.anjoguarda.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_items")
public class ImportItem {

    @Id
    private UUID id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_data", nullable = false, columnDefinition = "jsonb")
    private String proposedData;

    @Column(nullable = false)
    private int confidence;

    private Boolean approved;

    private String resolution;

    @Column(name = "resulting_entity_type")
    private String resultingEntityType;

    @Column(name = "resulting_entity_id")
    private UUID resultingEntityId;

    protected ImportItem() {}

    public ImportItem(UUID id, UUID importId, int position, String type, String proposedData) {
        this.id = id;
        this.importId = importId;
        this.position = position;
        this.type = type;
        this.proposedData = proposedData;
        this.confidence = 1;
    }

    public void reject() {
        this.approved = false;
        this.resolution = null;
        this.resultingEntityType = null;
        this.resultingEntityId = null;
    }

    public void accept(String resolution, String entityType, UUID entityId) {
        this.approved = true;
        this.resolution = resolution;
        this.resultingEntityType = entityType;
        this.resultingEntityId = entityId;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getProposedData() {
        return proposedData;
    }

    public int getConfidence() {
        return confidence;
    }

    public Boolean getApproved() {
        return approved;
    }

    public String getResolution() {
        return resolution;
    }

    public String getResultingEntityType() {
        return resultingEntityType;
    }

    public UUID getResultingEntityId() {
        return resultingEntityId;
    }
}
