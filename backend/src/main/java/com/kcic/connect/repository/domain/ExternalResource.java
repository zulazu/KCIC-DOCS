package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_resources", schema = "repository")
public class ExternalResource {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false, length = 255)
    private String normalizedHost;

    @Column(length = 40)
    private String providerCode;

    @Column(length = 240)
    private String linkTitle;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected ExternalResource() {
    }

    public ExternalResource(UUID id, String url, String normalizedHost, String providerCode, String linkTitle,
                            UUID createdBy, Instant now) {
        this.id = id;
        this.url = url;
        this.normalizedHost = normalizedHost;
        this.providerCode = providerCode;
        this.linkTitle = linkTitle;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getNormalizedHost() {
        return normalizedHost;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getLinkTitle() {
        return linkTitle;
    }
}
