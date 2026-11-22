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
@Table(name = "document_favorites", schema = "repository")
public class DocumentFavorite {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private Instant createdAt;

    protected DocumentFavorite() {
    }

    public DocumentFavorite(UUID userId, UUID documentId, Instant createdAt) {
        this.id = new Key(userId, documentId);
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return id.userId;
    }

    public UUID getDocumentId() {
        return id.documentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "document_id", nullable = false)
        private UUID documentId;

        protected Key() {
        }

        public Key(UUID userId, UUID documentId) {
            this.userId = userId;
            this.documentId = documentId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key && Objects.equals(userId, key.userId)
                    && Objects.equals(documentId, key.documentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, documentId);
        }
    }
}
