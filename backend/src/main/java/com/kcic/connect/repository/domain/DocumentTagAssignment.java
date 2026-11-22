package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_tag_assignments", schema = "repository")
public class DocumentTagAssignment {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private UUID assignedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected DocumentTagAssignment() {
    }

    public DocumentTagAssignment(UUID documentId, UUID tagId, UUID assignedBy, Instant createdAt) {
        this.id = new Key(documentId, tagId);
        this.assignedBy = assignedBy;
        this.createdAt = createdAt;
    }

    public UUID getDocumentId() {
        return id.documentId;
    }

    public UUID getTagId() {
        return id.tagId;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "document_id", nullable = false)
        private UUID documentId;

        @Column(name = "tag_id", nullable = false)
        private UUID tagId;

        protected Key() {
        }

        public Key(UUID documentId, UUID tagId) {
            this.documentId = documentId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key && Objects.equals(documentId, key.documentId)
                    && Objects.equals(tagId, key.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(documentId, tagId);
        }
    }
}
