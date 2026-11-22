package com.kcic.connect.template.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.masterdata.domain.DocumentCategory;
import com.kcic.connect.masterdata.infrastructure.DocumentCategoryRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.application.DocumentAccessService;
import com.kcic.connect.repository.application.DocumentService;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.DocumentStatus;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.VersionStatus;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.repository.infrastructure.DocumentVersionRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.template.api.TemplateDtos.CreateTemplateRequest;
import com.kcic.connect.template.api.TemplateDtos.RecordTemplateUsageRequest;
import com.kcic.connect.template.api.TemplateDtos.TemplateDetailResponse;
import com.kcic.connect.template.api.TemplateDtos.TemplatePermissions;
import com.kcic.connect.template.api.TemplateDtos.TemplateSummaryResponse;
import com.kcic.connect.template.api.TemplateDtos.TemplateUsageResponse;
import com.kcic.connect.template.api.TemplateDtos.TemplateVersionResponse;
import com.kcic.connect.template.api.TemplateDtos.UpdateTemplateRequest;
import com.kcic.connect.template.domain.DocumentTemplate;
import com.kcic.connect.template.domain.TemplateStatus;
import com.kcic.connect.template.domain.TemplateUsageEvent;
import com.kcic.connect.template.domain.TemplateUsageType;
import com.kcic.connect.template.infrastructure.DocumentTemplateRepository;
import com.kcic.connect.template.infrastructure.TemplateUsageEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int CATALOG_SCAN_LIMIT = 1000;

    private final DocumentTemplateRepository templateRepository;
    private final TemplateUsageEventRepository usageEventRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final DocumentService documentService;
    private final DocumentAccessService accessService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public TemplateService(DocumentTemplateRepository templateRepository,
                           TemplateUsageEventRepository usageEventRepository,
                           DocumentRepository documentRepository,
                           DocumentVersionRepository versionRepository,
                           DocumentCategoryRepository categoryRepository,
                           UserRepository userRepository,
                           OrganizationUnitRepository organizationUnitRepository,
                           DocumentService documentService,
                           DocumentAccessService accessService,
                           AuthorizationService authorizationService,
                           AuditService auditService,
                           Clock clock) {
        this.templateRepository = templateRepository;
        this.usageEventRepository = usageEventRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.documentService = documentService;
        this.accessService = accessService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateSummaryResponse> list(UUID actor, String keyword, String statusParam,
                                                      UUID categoryId, UUID ownerUserId,
                                                      UUID organizationUnitId, int page, int size) {
        authorizationService.requireCapability(actor, PermissionCodes.TEMPLATE_VIEW);
        TemplateStatus status = parseStatus(statusParam);
        boolean administer = canAdminister(actor);
        boolean canCreate = authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_CREATE);
        if (status != TemplateStatus.PUBLISHED && !administer && !canCreate) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat template non-publikasi.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<DocumentTemplate> spec = specification(status, keyword, categoryId, ownerUserId,
                organizationUnitId);
        List<DocumentTemplate> matches = templateRepository.findAll(spec,
                PageRequest.of(0, CATALOG_SCAN_LIMIT, Sort.by(Sort.Order.asc("templateName")))).getContent();

        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        boolean canViewTemplates = authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_VIEW);
        Map<UUID, Document> documents = loadDocuments(matches);
        List<DocumentTemplate> visible = new ArrayList<>();
        for (DocumentTemplate template : matches) {
            Document document = documents.get(template.getDocumentId());
            if (document == null) {
                continue;
            }
            if (administer) {
                visible.add(template);
                continue;
            }
            if (template.getTemplateStatus() == TemplateStatus.PUBLISHED && canViewTemplates
                    && canViewDocument(ctx, document)) {
                visible.add(template);
            } else if (canCreate && canViewDocument(ctx, document)) {
                visible.add(template);
            }
        }

        int from = Math.min(safePage * safeSize, visible.size());
        int to = Math.min(from + safeSize, visible.size());
        List<TemplateSummaryResponse> items = summaryLookup(visible.subList(from, to)).summarize(
                visible.subList(from, to));
        return PageResponse.of(items, safePage, safeSize, visible.size());
    }

    @Transactional(readOnly = true)
    public TemplateDetailResponse detail(UUID actor, UUID templateId) {
        DocumentTemplate template = loadTemplate(templateId);
        TemplateView view = view(actor, template);
        if (!view.visible()) {
            throw ApiException.notFound("Template tidak ditemukan.");
        }
        return detailView(template, view);
    }

    @Transactional
    public TemplateDetailResponse create(UUID actor, CreateTemplateRequest request) {
        authorizationService.requireCapability(actor, PermissionCodes.TEMPLATE_CREATE);
        Document document = documentService.loadDocument(request.documentId());
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        if (!canViewDocument(ctx, document)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat dokumen sumber template ini.");
        }
        if (document.getDocumentStatus() == DocumentStatus.ARCHIVED) {
            throw ApiException.conflict("Dokumen yang diarsipkan tidak dapat dijadikan template.");
        }
        if (templateRepository.findByDocumentId(document.getId()).isPresent()) {
            throw ApiException.conflict("Dokumen ini sudah terdaftar sebagai template.");
        }
        String code = normalizedCode(request.templateCode());
        if (templateRepository.existsByTemplateCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode template sudah digunakan.");
        }
        Instant now = Instant.now(clock);
        DocumentTemplate template = templateRepository.save(new DocumentTemplate(UUID.randomUUID(), document.getId(), code,
                request.templateName().trim(), trimToNull(request.description()), actor, now));
        auditService.record(actor, "TEMPLATE_CREATED", "TEMPLATE", template.getId(), "SUCCESS",
                Map.of("templateCode", code, "documentId", document.getId().toString()));
        return detailView(template, view(actor, template));
    }

    @Transactional
    public TemplateDetailResponse update(UUID actor, UUID templateId, UpdateTemplateRequest request) {
        authorizationService.requireCapability(actor, PermissionCodes.TEMPLATE_CREATE);
        DocumentTemplate template = loadTemplate(templateId);
        TemplateView view = view(actor, template);
        if (!view.visible() || !(view.canCreate() || view.canAdminister())) {
            throw ApiException.notFound("Template tidak ditemukan.");
        }
        String code = normalizedCode(request.templateCode());
        if (templateRepository.existsByTemplateCodeIgnoreCaseExcluding(code, template.getId())) {
            throw ApiException.conflict("Kode template sudah digunakan.");
        }
        template.updateMetadata(code, request.templateName().trim(), trimToNull(request.description()), actor,
                Instant.now(clock));
        auditService.record(actor, "TEMPLATE_UPDATED", "TEMPLATE", template.getId(), "SUCCESS",
                Map.of("templateCode", code));
        return detailView(template, view(actor, template));
    }

    @Transactional
    public TemplateDetailResponse publish(UUID actor, UUID templateId, UUID requestedVersionId) {
        authorizationService.requireCapability(actor, PermissionCodes.TEMPLATE_PUBLISH);
        DocumentTemplate template = loadTemplate(templateId);
        Document document = documentService.loadDocument(template.getDocumentId());
        if (document.getDocumentStatus() == DocumentStatus.ARCHIVED) {
            throw ApiException.conflict("Dokumen sumber template telah diarsipkan.");
        }
        if (template.getTemplateStatus() == TemplateStatus.ARCHIVED) {
            throw ApiException.conflict("Template yang diarsipkan tidak dapat diterbitkan kembali.");
        }
        DocumentVersion version = requestedVersionId == null
                ? versionRepository.findFirstByDocumentIdAndVersionStatus(document.getId(), VersionStatus.CURRENT)
                        .orElseThrow(() -> ApiException.conflict(
                                "Dokumen belum memiliki versi aktif untuk diterbitkan."))
                : versionRepository.findByIdAndDocumentId(requestedVersionId, document.getId())
                        .orElseThrow(() -> ApiException.notFound("Versi dokumen tidak ditemukan."));
        if (version.getVersionStatus() == VersionStatus.WITHDRAWN) {
            throw ApiException.conflict("Versi yang ditarik tidak dapat diterbitkan sebagai template.");
        }
        if (version.getVersionStatus() != VersionStatus.CURRENT) {
            throw ApiException.conflict("Hanya versi aktif dokumen yang dapat diterbitkan sebagai template.");
        }
        if (template.getTemplateStatus() == TemplateStatus.PUBLISHED
                && Objects.equals(template.getCurrentPublishedVersionId(), version.getId())) {
            return detailView(template, view(actor, template));
        }
        UUID previousVersionId = template.getCurrentPublishedVersionId();
        template.publish(version.getId(), actor, Instant.now(clock));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("versionNumber", version.getVersionNumber());
        metadata.put("documentId", document.getId().toString());
        if (previousVersionId != null) {
            versionRepository.findById(previousVersionId).ifPresent(previous ->
                    metadata.put("previousVersionNumber", previous.getVersionNumber()));
        }
        auditService.record(actor, "TEMPLATE_PUBLISHED", "TEMPLATE", template.getId(), "SUCCESS", metadata);
        return detailView(template, view(actor, template));
    }

    @Transactional
    public TemplateDetailResponse archive(UUID actor, UUID templateId) {
        authorizationService.requireCapability(actor, PermissionCodes.TEMPLATE_ARCHIVE);
        DocumentTemplate template = loadTemplate(templateId);
        if (template.getTemplateStatus() == TemplateStatus.ARCHIVED) {
            throw ApiException.conflict("Template sudah diarsipkan.");
        }
        TemplateStatus previous = template.getTemplateStatus();
        template.archive(actor, Instant.now(clock));
        auditService.record(actor, "TEMPLATE_ARCHIVED", "TEMPLATE", template.getId(), "SUCCESS",
                Map.of("previousStatus", previous.name()));
        return detailView(template, view(actor, template));
    }

    @Transactional
    public TemplateUsageResponse recordUsage(UUID actor, UUID templateId, RecordTemplateUsageRequest request) {
        TemplateUsageType usageType = parseUsageType(request.usageType());
        DocumentTemplate template = loadTemplate(templateId);
        TemplateView view = view(actor, template);
        if (!view.visible() || template.getTemplateStatus() != TemplateStatus.PUBLISHED
                || template.getCurrentPublishedVersionId() == null) {
            throw ApiException.notFound("Template tidak ditemukan.");
        }
        switch (usageType) {
            case VIEW, COPY -> requireDocumentPermission(view.canViewDocument(),
                    "Anda tidak memiliki izin untuk membuka template ini.");
            case DOWNLOAD -> requireDocumentPermission(view.canDownloadDocument(),
                    "Anda tidak memiliki izin untuk mengunduh template ini.");
        }
        DocumentVersion version = versionRepository.findById(template.getCurrentPublishedVersionId())
                .filter(candidate -> candidate.getDocumentId().equals(template.getDocumentId()))
                .orElseThrow(() -> ApiException.conflict("Versi template tidak tersedia."));
        Instant now = Instant.now(clock);
        usageEventRepository.save(new TemplateUsageEvent(UUID.randomUUID(), template.getId(), version.getId(), actor,
                usageType, now));
        auditService.record(actor, "TEMPLATE_USED", "TEMPLATE", template.getId(), "SUCCESS",
                Map.of("usageType", usageType.name(), "versionNumber", version.getVersionNumber()));
        return new TemplateUsageResponse(template.getId(), usageType.name(), version.getVersionNumber(), now);
    }

    // ----------------------------------------------------------- visibility

    private record TemplateView(DocumentTemplate template, Document document,
                                DocumentAccessService.ActorContext ctx, boolean canViewDocument,
                                boolean canDownloadDocument, boolean canAdminister, boolean canCreate,
                                boolean visible) {
    }

    private TemplateView view(UUID actor, DocumentTemplate template) {
        Document document = documentService.loadDocument(template.getDocumentId());
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        boolean canAdminister = canAdminister(actor);
        boolean canCreate = authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_CREATE);
        boolean canViewDocument = canViewDocument(ctx, document);
        boolean canViewTemplates = authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_VIEW);
        boolean visible = canAdminister
                || (template.getTemplateStatus() == TemplateStatus.PUBLISHED && canViewTemplates && canViewDocument)
                || (canCreate && canViewDocument);
        return new TemplateView(template, document, ctx, canViewDocument, canDownloadDocument(ctx, document),
                canAdminister, canCreate, visible);
    }

    private boolean canAdminister(UUID actor) {
        return authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_PUBLISH)
                || authorizationService.hasCapability(actor, PermissionCodes.TEMPLATE_ARCHIVE);
    }

    private boolean canViewDocument(DocumentAccessService.ActorContext ctx, Document document) {
        DocumentService.Grants grants = documentService.grants(document);
        return accessService.canView(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants());
    }

    private boolean canDownloadDocument(DocumentAccessService.ActorContext ctx, Document document) {
        DocumentService.Grants grants = documentService.grants(document);
        return accessService.canDownload(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants());
    }

    // -------------------------------------------------------------- mapping

    private TemplateDetailResponse detailView(DocumentTemplate template, TemplateView view) {
        SummaryLookup lookup = summaryLookup(List.of(template));
        TemplateSummaryResponse summary = lookup.summarize(List.of(template)).get(0);
        boolean versionHistoryVisible = view.visible() && view.canViewDocument();
        List<TemplateVersionResponse> history = versionHistoryVisible
                ? versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(template.getDocumentId()).stream()
                        .map(version -> versionView(version, template))
                        .toList()
                : List.of();
        String changeSummary = null;
        if (template.getCurrentPublishedVersionId() != null) {
            changeSummary = versionRepository.findById(template.getCurrentPublishedVersionId())
                    .map(DocumentVersion::getChangeSummary)
                    .orElse(null);
        }
        long viewCount = usageEventRepository.countByTemplateIdAndUsageType(template.getId(), TemplateUsageType.VIEW);
        long downloadCount = usageEventRepository.countByTemplateIdAndUsageType(template.getId(),
                TemplateUsageType.DOWNLOAD);
        long copyCount = usageEventRepository.countByTemplateIdAndUsageType(template.getId(), TemplateUsageType.COPY);
        TemplatePermissions permissions = new TemplatePermissions(
                view.visible() && (view.canCreate() || view.canAdminister()),
                view.canAdminister(),
                view.canAdminister(),
                view.canViewDocument(),
                view.canDownloadDocument());
        Document document = view.document();
        return new TemplateDetailResponse(summary, document.getDescription(), document.getEffectiveDate(),
                document.getReviewDate(), document.getExpiryDate(), document.getUpdatedAt(), changeSummary,
                history, versionHistoryVisible, viewCount + downloadCount + copyCount, viewCount, downloadCount,
                copyCount, permissions);
    }

    private TemplateVersionResponse versionView(DocumentVersion version, DocumentTemplate template) {
        return new TemplateVersionResponse(version.getId(), version.getVersionNumber(), version.getVersionLabel(),
                version.getContentSource().name(), version.getVersionStatus().name(),
                version.getChangeSummary(), version.getPublishedAt(),
                Objects.equals(template.getCurrentPublishedVersionId(), version.getId()));
    }

    private SummaryLookup summaryLookup(List<DocumentTemplate> templates) {
        Set<UUID> documentIds = templates.stream().map(DocumentTemplate::getDocumentId)
                .collect(Collectors.toSet());
        Set<UUID> versionIds = templates.stream().map(DocumentTemplate::getCurrentPublishedVersionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Document> documents = documentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<UUID, DocumentVersion> versions = versionIds.isEmpty() ? Map.of()
                : versionRepository.findAllById(versionIds).stream()
                        .collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        Set<UUID> categoryIds = documents.values().stream().map(Document::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, DocumentCategory> categories = categoryIds.isEmpty() ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(DocumentCategory::getId, Function.identity()));
        Set<UUID> unitIds = new java.util.HashSet<>();
        Set<UUID> userIds = new java.util.HashSet<>();
        documents.values().forEach(document -> {
            if (document.getOwnerOrganizationUnitId() != null) {
                unitIds.add(document.getOwnerOrganizationUnitId());
            }
            if (document.getOwnerUserId() != null) {
                userIds.add(document.getOwnerUserId());
            }
        });
        templates.stream().map(DocumentTemplate::getPublishedBy).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, OrganizationUnit> units = unitIds.isEmpty() ? Map.of()
                : organizationUnitRepository.findAllById(unitIds).stream()
                        .collect(Collectors.toMap(OrganizationUnit::getId, Function.identity()));
        Map<UUID, User> users = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        return new SummaryLookup(documents, versions, categories, units, users);
    }

    private record SummaryLookup(Map<UUID, Document> documents, Map<UUID, DocumentVersion> versions,
                                 Map<UUID, DocumentCategory> categories, Map<UUID, OrganizationUnit> units,
                                 Map<UUID, User> users) {

        List<TemplateSummaryResponse> summarize(List<DocumentTemplate> templates) {
            List<TemplateSummaryResponse> responses = new ArrayList<>();
            for (DocumentTemplate template : templates) {
                Document document = documents.get(template.getDocumentId());
                if (document == null) {
                    continue;
                }
                DocumentCategory category = categories.get(document.getCategoryId());
                OrganizationUnit unit = document.getOwnerOrganizationUnitId() == null ? null
                        : units.get(document.getOwnerOrganizationUnitId());
                User owner = document.getOwnerUserId() == null ? null : users.get(document.getOwnerUserId());
                User publisher = template.getPublishedBy() == null ? null : users.get(template.getPublishedBy());
                DocumentVersion version = template.getCurrentPublishedVersionId() == null ? null
                        : versions.get(template.getCurrentPublishedVersionId());
                responses.add(new TemplateSummaryResponse(template.getId(), template.getTemplateCode(),
                        template.getTemplateName(), template.getDescription(), template.getTemplateStatus().name(),
                        document.getId(), document.getDocumentCode(), document.getTitle(),
                        document.getDocumentStatus().name(), document.getCategoryId(),
                        category == null ? null : category.getCategoryName(), document.getOwnerUserId(),
                        owner == null ? null : owner.getDisplayName(), document.getOwnerOrganizationUnitId(),
                        unit == null ? null : unit.getUnitName(), template.getCurrentPublishedVersionId(),
                        version == null ? null : version.getVersionNumber(),
                        version == null ? null : version.getVersionLabel(),
                        version == null ? null : version.getContentSource().name(), template.getPublishedAt(),
                        publisher == null ? null : publisher.getDisplayName(), template.getCreatedAt(),
                        template.getUpdatedAt()));
            }
            return responses;
        }
    }

    // ------------------------------------------------------------ filtering

    private Specification<DocumentTemplate> specification(TemplateStatus status, String keyword, UUID categoryId,
                                                          UUID ownerUserId, UUID organizationUnitId) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("templateStatus"), status));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = likePattern(keyword);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("templateName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("templateCode")), pattern, '\\')));
            }
            if (categoryId != null || ownerUserId != null || organizationUnitId != null) {
                jakarta.persistence.criteria.Subquery<UUID> subquery = query.subquery(UUID.class);
                jakarta.persistence.criteria.Root<Document> document = subquery.from(Document.class);
                List<jakarta.persistence.criteria.Predicate> documentPredicates = new ArrayList<>();
                documentPredicates.add(cb.equal(document.get("id"), root.get("documentId")));
                if (categoryId != null) {
                    documentPredicates.add(cb.equal(document.get("categoryId"), categoryId));
                }
                if (ownerUserId != null) {
                    documentPredicates.add(cb.equal(document.get("ownerUserId"), ownerUserId));
                }
                if (organizationUnitId != null) {
                    documentPredicates.add(cb.equal(document.get("ownerOrganizationUnitId"), organizationUnitId));
                }
                subquery.select(document.get("id")).where(documentPredicates.toArray(
                        new jakarta.persistence.criteria.Predicate[0]));
                predicates.add(cb.exists(subquery));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Map<UUID, Document> loadDocuments(List<DocumentTemplate> templates) {
        Set<UUID> ids = templates.stream().map(DocumentTemplate::getDocumentId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, Document> map = new HashMap<>();
        documentRepository.findAllById(ids).forEach(document -> map.put(document.getId(), document));
        return map;
    }

    // -------------------------------------------------------------- helpers

    private DocumentTemplate loadTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("Template tidak ditemukan."));
    }

    private static TemplateStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return TemplateStatus.PUBLISHED;
        }
        try {
            return TemplateStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "Status template tidak dikenali.");
        }
    }

    private static TemplateUsageType parseUsageType(String value) {
        try {
            return TemplateUsageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.validation("usageType", "Jenis penggunaan template tidak dikenali.");
        }
    }

    private static void requireDocumentPermission(boolean allowed, String message) {
        if (!allowed) {
            throw ApiException.forbidden(message);
        }
    }

    private static String normalizedCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String likePattern(String keyword) {
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
