package com.kcic.connect.repository.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.audit.domain.AuditEvent;
import com.kcic.connect.audit.infrastructure.AuditEventRepository;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.masterdata.domain.DocumentCategory;
import com.kcic.connect.masterdata.domain.DocumentClassification;
import com.kcic.connect.masterdata.domain.DocumentType;
import com.kcic.connect.masterdata.domain.Tag;
import com.kcic.connect.masterdata.infrastructure.DocumentCategoryRepository;
import com.kcic.connect.masterdata.infrastructure.DocumentClassificationRepository;
import com.kcic.connect.masterdata.infrastructure.DocumentTypeRepository;
import com.kcic.connect.masterdata.infrastructure.TagRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.api.DocumentDtos.ActivityResponse;
import com.kcic.connect.repository.api.DocumentDtos.CreateDocumentRequest;
import com.kcic.connect.repository.api.DocumentDtos.DocumentDetailResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentPermissions;
import com.kcic.connect.repository.api.DocumentDtos.DocumentSummaryResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentVersionResponse;
import com.kcic.connect.repository.api.DocumentDtos.UpdateDocumentRequest;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.DocumentFavorite;
import com.kcic.connect.repository.domain.DocumentOwnerHistory;
import com.kcic.connect.repository.domain.DocumentStatus;
import com.kcic.connect.repository.domain.DocumentTagAssignment;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.domain.FolderStatus;
import com.kcic.connect.repository.domain.VersionStatus;
import com.kcic.connect.repository.domain.Workspace;
import com.kcic.connect.repository.domain.WorkspaceStatus;
import com.kcic.connect.repository.infrastructure.DocumentFavoriteRepository;
import com.kcic.connect.repository.infrastructure.DocumentOwnerHistoryRepository;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.repository.infrastructure.DocumentTagAssignmentRepository;
import com.kcic.connect.repository.infrastructure.DocumentVersionRepository;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentOwnerHistoryRepository ownerHistoryRepository;
    private final DocumentFavoriteRepository favoriteRepository;
    private final DocumentTagAssignmentRepository tagAssignmentRepository;
    private final FolderRepository folderRepository;
    private final FolderGrantResolver folderGrantResolver;
    private final TagRepository tagRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final DocumentTypeRepository typeRepository;
    private final DocumentClassificationRepository classificationRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final DocumentAccessService accessService;
    private final DocumentResponseMapper mapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository versionRepository,
                           DocumentOwnerHistoryRepository ownerHistoryRepository,
                           DocumentFavoriteRepository favoriteRepository,
                           DocumentTagAssignmentRepository tagAssignmentRepository,
                           FolderRepository folderRepository,
                           FolderGrantResolver folderGrantResolver,
                           TagRepository tagRepository,
                           DocumentCategoryRepository categoryRepository,
                           DocumentTypeRepository typeRepository,
                           DocumentClassificationRepository classificationRepository,
                           OrganizationUnitRepository organizationUnitRepository,
                           UserRepository userRepository,
                           WorkspaceService workspaceService,
                           DocumentAccessService accessService,
                           DocumentResponseMapper mapper,
                           AuthorizationService authorizationService,
                           AuditService auditService,
                           AuditEventRepository auditEventRepository,
                           Clock clock) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.ownerHistoryRepository = ownerHistoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.tagAssignmentRepository = tagAssignmentRepository;
        this.folderRepository = folderRepository;
        this.folderGrantResolver = folderGrantResolver;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
        this.typeRepository = typeRepository;
        this.classificationRepository = classificationRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.accessService = accessService;
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- search

    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> search(UUID actor, UUID workspaceId, String keyword,
                                                        UUID categoryId, UUID documentTypeId,
                                                        UUID classificationId, UUID ownerUserId,
                                                        UUID ownerUnitId, UUID tagId, String status,
                                                        boolean includeArchived, int page, int size,
                                                        String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        DocumentStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        Specification<Document> specification = buildSearchSpecification(workspaceId, trimToNull(keyword),
                categoryId, documentTypeId, classificationId, ownerUserId, ownerUnitId, tagId, statusFilter,
                includeArchived);
        List<Document> candidates = documentRepository.findAll(specification, parseSort(sort));
        if (candidates.isEmpty()) {
            return PageResponse.of(List.of(), safePage, safeSize, 0);
        }
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        List<UUID> documentIds = candidates.stream().map(Document::getId).toList();
        Set<UUID> workspaceIds = candidates.stream().map(Document::getWorkspaceId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<ResourcePermission>> documentGrants = authorizationService
                .activeResourcePermissions(ResourceType.DOCUMENT, documentIds);
        Map<UUID, List<ResourcePermission>> folderGrants = folderGrantResolver.grantsFor(candidates.stream()
                .map(Document::getFolderId).toList());
        Map<UUID, List<ResourcePermission>> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE, workspaceIds);
        List<Document> visible = candidates.stream()
                .filter(document -> accessService.canView(ctx, document,
                        documentGrants.getOrDefault(document.getId(), List.of()),
                        folderGrants.getOrDefault(document.getFolderId(), List.of()),
                        workspaceGrants.getOrDefault(document.getWorkspaceId(), List.of())))
                .toList();
        Set<UUID> favoriteIds = favoriteDocumentIds(actor);
        List<DocumentSummaryResponse> summaries = mapper.summaries(visible, favoriteIds);
        int from = Math.min(safePage * safeSize, summaries.size());
        int to = Math.min(from + safeSize, summaries.size());
        return PageResponse.of(summaries.subList(from, to), safePage, safeSize, summaries.size());
    }

    private Specification<Document> buildSearchSpecification(UUID workspaceId, String keyword, UUID categoryId,
                                                            UUID documentTypeId, UUID classificationId,
                                                            UUID ownerUserId, UUID ownerUnitId, UUID tagId,
                                                            DocumentStatus status, boolean includeArchived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (workspaceId != null) {
                predicates.add(cb.equal(root.get("workspaceId"), workspaceId));
            }
            if (keyword != null) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("documentCode"), "")), pattern)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (documentTypeId != null) {
                predicates.add(cb.equal(root.get("documentTypeId"), documentTypeId));
            }
            if (classificationId != null) {
                predicates.add(cb.equal(root.get("classificationId"), classificationId));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), ownerUserId));
            }
            if (ownerUnitId != null) {
                predicates.add(cb.equal(root.get("ownerOrganizationUnitId"), ownerUnitId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("documentStatus"), status));
            } else if (!includeArchived) {
                predicates.add(cb.notEqual(root.get("documentStatus"), DocumentStatus.ARCHIVED));
            }
            if (tagId != null && query != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<DocumentTagAssignment> assignment = subquery.from(DocumentTagAssignment.class);
                subquery.select(assignment.get("id").get("documentId"))
                        .where(cb.equal(assignment.get("id").get("tagId"), tagId));
                predicates.add(root.get("id").in(subquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Sort parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "-updatedAt" : sort.trim();
        boolean descending = value.startsWith("-");
        String field = descending ? value.substring(1) : value;
        String property = switch (field) {
            case "title" -> "title";
            case "documentCode" -> "documentCode";
            case "createdAt" -> "createdAt";
            default -> "updatedAt";
        };
        return Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public DocumentDetailResponse create(UUID actor, UUID workspaceId, CreateDocumentRequest request) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        if (!accessService.canCreateInWorkspace(actor, workspaceId, workspace.getOwnerOrganizationUnitId())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk membuat dokumen di workspace ini.");
        }
        if (workspace.getWorkspaceStatus() == WorkspaceStatus.ARCHIVED) {
            throw ApiException.conflict("Workspace yang diarsipkan tidak dapat ditambahi dokumen.");
        }
        requireMetadataReferences(workspaceId, request.folderId(), request.categoryId(),
                request.documentTypeId(), request.classificationId());
        OwnerRef owner = resolveOwner(actor, request.ownerUserId(), request.ownerOrganizationUnitId());
        String code = trimToNull(request.documentCode());
        if (code != null && documentRepository.existsByDocumentCodeIgnoreCase(code)) {
            throw ApiException.validation("documentCode", "Kode dokumen sudah digunakan.");
        }
        Instant now = Instant.now(clock);
        Document document = documentRepository.save(new Document(UUID.randomUUID(), code, request.title().trim(),
                trimToNull(request.description()), workspaceId, request.folderId(), request.categoryId(),
                request.documentTypeId(), request.classificationId(), owner.ownerUserId(),
                owner.ownerOrganizationUnitId(), actor, now));
        ownerHistoryRepository.save(new DocumentOwnerHistory(UUID.randomUUID(), document.getId(),
                owner.ownerUserId(), owner.ownerOrganizationUnitId(), actor, "Pemilik awal dokumen", now));
        replaceTags(document.getId(), request.tagIds(), actor);
        auditService.record(actor, "DOCUMENT_CREATED", "DOCUMENT", document.getId(), "SUCCESS",
                Map.of("workspaceId", workspaceId.toString(), "title", document.getTitle(),
                        "status", document.getDocumentStatus().name()));
        return detail(actor, document.getId());
    }

    // ---------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public DocumentDetailResponse detail(UUID actor, UUID documentId) {
        Document document = loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        if (!accessService.canView(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat dokumen ini.");
        }
        List<DocumentVersion> versions = versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId);
        List<DocumentOwnerHistory> ownerHistory =
                ownerHistoryRepository.findByDocumentIdOrderByValidFromDesc(documentId);
        DocumentPermissions permissions = permissions(ctx, document, grants);
        boolean favorited = favoriteRepository.existsById(
                new DocumentFavorite.Key(actor, document.getId()));
        DocumentSummaryResponse summary = mapper.summary(document, favorited);
        return mapper.detail(document, summary, versions, ownerHistory, permissions);
    }

    public Document loadDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Dokumen tidak ditemukan."));
    }

    public Grants grants(Document document) {
        List<ResourcePermission> documentGrants = authorizationService
                .activeResourcePermissions(ResourceType.DOCUMENT, document.getId());
        List<ResourcePermission> folderGrants = folderGrantResolver.grantsFor(document.getFolderId());
        List<ResourcePermission> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE, document.getWorkspaceId());
        return new Grants(documentGrants, folderGrants, workspaceGrants);
    }

    public record Grants(List<ResourcePermission> documentGrants, List<ResourcePermission> folderGrants,
                         List<ResourcePermission> workspaceGrants) {
    }

    public DocumentPermissions permissions(DocumentAccessService.ActorContext ctx, Document document,
                                           Grants grants) {
        return new DocumentPermissions(
                accessService.canEdit(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canUpload(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canCreateVersion(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canArchive(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canRestore(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canPreview(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canDownload(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants()),
                accessService.canManageAccess(ctx, document));
    }

    // ---------------------------------------------------------------- update

    @Transactional
    public DocumentDetailResponse update(UUID actor, UUID documentId, UpdateDocumentRequest request) {
        Document document = loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        if (!accessService.canEdit(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk mengubah dokumen ini.");
        }
        requireWorkspaceWritable(document.getWorkspaceId());
        requireMetadataReferences(document.getWorkspaceId(), request.folderId(), request.categoryId(),
                request.documentTypeId(), request.classificationId());
        OwnerRef owner = resolveOwner(actor, request.ownerUserId(), request.ownerOrganizationUnitId());
        String code = trimToNull(request.documentCode());
        if (code != null && documentRepository.existsByDocumentCodeIgnoreCaseExcluding(code, documentId)) {
            throw ApiException.validation("documentCode", "Kode dokumen sudah digunakan.");
        }
        boolean ownerChanged = !Objects.equals(owner.ownerUserId(), document.getOwnerUserId())
                || !Objects.equals(owner.ownerOrganizationUnitId(), document.getOwnerOrganizationUnitId());
        if (ownerChanged) {
            String reason = trimToNull(request.ownerChangeReason());
            if (reason == null || reason.length() < 5) {
                throw ApiException.validation("ownerChangeReason",
                        "Alasan perubahan owner wajib diisi minimal 5 karakter.");
            }
            boolean canChangeOwner = authorizationService.allowsResourceCapability(actor,
                    PermissionCodes.DOCUMENT_UPDATE_METADATA, document.getWorkspaceId(),
                    ownerUnitSet(document), true);
            if (!canChangeOwner) {
                throw ApiException.forbidden("Anda tidak memiliki izin untuk mengubah pemilik dokumen.");
            }
            Instant now = Instant.now(clock);
            ownerHistoryRepository.findFirstByDocumentIdAndValidUntilIsNull(documentId)
                    .ifPresent(history -> {
                        history.close(now);
                        // Flush the close before inserting the new open row; the partial
                        // unique index allows only one row with valid_until IS NULL.
                        ownerHistoryRepository.flush();
                    });
            ownerHistoryRepository.save(new DocumentOwnerHistory(UUID.randomUUID(), documentId,
                    owner.ownerUserId(), owner.ownerOrganizationUnitId(), actor, reason, now));
            auditService.record(actor, "DOCUMENT_OWNER_CHANGED", "DOCUMENT", documentId, "SUCCESS",
                    Map.of("previousOwnerUserId", String.valueOf(document.getOwnerUserId()),
                            "previousOwnerUnitId", String.valueOf(document.getOwnerOrganizationUnitId()),
                            "newOwnerUserId", String.valueOf(owner.ownerUserId()),
                            "newOwnerUnitId", String.valueOf(owner.ownerOrganizationUnitId()),
                            "reason", reason));
        }
        document.updateMetadata(code, request.title().trim(), trimToNull(request.description()), request.folderId(),
                request.categoryId(), request.documentTypeId(), request.classificationId(), owner.ownerUserId(),
                owner.ownerOrganizationUnitId(), request.effectiveDate(), request.reviewDate(),
                request.expiryDate(), actor, Instant.now(clock));
        replaceTags(documentId, request.tagIds(), actor);
        auditService.record(actor, "DOCUMENT_METADATA_UPDATED", "DOCUMENT", documentId, "SUCCESS",
                Map.of("title", document.getTitle()));
        return detail(actor, documentId);
    }

    // ------------------------------------------------------------- lifecycle

    @Transactional
    public DocumentDetailResponse activate(UUID actor, UUID documentId) {
        return changeStatus(actor, documentId, DocumentStatus.ACTIVE, "DOCUMENT_ACTIVATED");
    }

    @Transactional
    public DocumentDetailResponse archive(UUID actor, UUID documentId) {
        return changeStatus(actor, documentId, DocumentStatus.ARCHIVED, "DOCUMENT_ARCHIVED");
    }

    @Transactional
    public DocumentDetailResponse restore(UUID actor, UUID documentId) {
        return changeStatus(actor, documentId, DocumentStatus.ACTIVE, "DOCUMENT_RESTORED");
    }

    private DocumentDetailResponse changeStatus(UUID actor, UUID documentId, DocumentStatus target,
                                                String auditAction) {
        Document document = loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        boolean allowed = target == DocumentStatus.ARCHIVED
                ? accessService.canArchive(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants())
                : accessService.canRestore(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants())
                || accessService.canEdit(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants());
        if (!allowed) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk mengubah status dokumen ini.");
        }
        requireWorkspaceWritable(document.getWorkspaceId());
        if (document.getDocumentStatus() == target) {
            return detail(actor, documentId);
        }
        if (!document.getDocumentStatus().canTransitionTo(target)) {
            throw ApiException.conflict("Perpindahan status dokumen dari " + document.getDocumentStatus()
                    + " ke " + target + " tidak diizinkan.");
        }
        document.changeStatus(target, actor, Instant.now(clock));
        auditService.record(actor, auditAction, "DOCUMENT", documentId, "SUCCESS",
                Map.of("status", target.name()));
        return detail(actor, documentId);
    }

    // -------------------------------------------------------------- versions

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> versions(UUID actor, UUID documentId) {
        Document document = loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        if (!accessService.canView(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat versi dokumen ini.");
        }
        boolean canPreview = accessService.canPreview(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        boolean canDownload = accessService.canDownload(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        return mapper.versions(versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId),
                canPreview, canDownload);
    }

    public List<DocumentVersion> currentVersions(List<UUID> documentIds) {
        return versionRepository.findAllByDocumentIdInAndVersionStatus(documentIds, VersionStatus.CURRENT);
    }

    // -------------------------------------------------------------- activity

    @Transactional(readOnly = true)
    public List<ActivityResponse> activity(UUID actor, UUID documentId) {
        Document document = loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        if (!accessService.canView(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat aktivitas dokumen ini.");
        }
        List<AuditEvent> events = auditEventRepository
                .findTop50ByResourceTypeAndResourceIdOrderByOccurredAtDesc("DOCUMENT", documentId);
        Map<UUID, String> names = mapper.userNames(events.stream().map(AuditEvent::getActorUserId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return events.stream()
                .map(event -> new ActivityResponse(event.getId(), event.getActionCode(), event.getOutcome(),
                        event.getActorUserId(), names.get(event.getActorUserId()), event.getOccurredAt(),
                        event.getSafeMetadata()))
                .toList();
    }

    // ------------------------------------------------------------- favorites

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> favorites(UUID actor) {
        List<DocumentFavorite> favorites = favoriteRepository.findAllByIdUserIdOrderByCreatedAtDesc(actor);
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<UUID> orderedIds = favorites.stream().map(DocumentFavorite::getDocumentId).toList();
        Map<UUID, Document> byId = documentRepository.findAllById(orderedIds).stream()
                .collect(java.util.stream.Collectors.toMap(Document::getId, doc -> doc));
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Map<UUID, List<ResourcePermission>> documentGrants = authorizationService
                .activeResourcePermissions(ResourceType.DOCUMENT, orderedIds);
        Map<UUID, List<ResourcePermission>> folderGrants = folderGrantResolver.grantsFor(byId.values().stream()
                .map(Document::getFolderId).toList());
        Map<UUID, List<ResourcePermission>> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE, byId.values().stream()
                        .map(Document::getWorkspaceId).collect(java.util.stream.Collectors.toSet()));
        List<Document> visible = orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(document -> accessService.canView(ctx, document,
                        documentGrants.getOrDefault(document.getId(), List.of()),
                        folderGrants.getOrDefault(document.getFolderId(), List.of()),
                        workspaceGrants.getOrDefault(document.getWorkspaceId(), List.of())))
                .toList();
        return mapper.summaries(visible, new HashSet<>(orderedIds));
    }

    @Transactional
    public void addFavorite(UUID actor, UUID documentId) {
        Document document = loadDocument(documentId);
        requireCanView(actor, document);
        DocumentFavorite.Key key = new DocumentFavorite.Key(actor, documentId);
        if (!favoriteRepository.existsById(key)) {
            favoriteRepository.save(new DocumentFavorite(actor, documentId, Instant.now(clock)));
        }
    }

    @Transactional
    public void removeFavorite(UUID actor, UUID documentId) {
        favoriteRepository.deleteById(new DocumentFavorite.Key(actor, documentId));
    }

    private void requireCanView(UUID actor, Document document) {
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        Grants grants = grants(document);
        if (!accessService.canView(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat dokumen ini.");
        }
    }

    private Set<UUID> favoriteDocumentIds(UUID actor) {
        return favoriteRepository.findAllByIdUserIdOrderByCreatedAtDesc(actor).stream()
                .map(DocumentFavorite::getDocumentId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // --------------------------------------------------------------- helpers

    private void requireMetadataReferences(UUID workspaceId, UUID folderId, UUID categoryId, UUID documentTypeId,
                                           UUID classificationId) {
        if (folderId != null) {
            Folder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> ApiException.validation("folderId", "Folder tidak ditemukan."));
            if (!folder.getWorkspaceId().equals(workspaceId)) {
                throw ApiException.validation("folderId", "Folder berada di workspace lain.");
            }
            if (folder.getFolderStatus() != FolderStatus.ACTIVE) {
                throw ApiException.validation("folderId", "Folder tidak aktif.");
            }
        }
        DocumentCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.validation("categoryId", "Kategori tidak ditemukan."));
        if (!category.isActive()) {
            throw ApiException.validation("categoryId", "Kategori tidak aktif.");
        }
        DocumentType type = typeRepository.findById(documentTypeId)
                .orElseThrow(() -> ApiException.validation("documentTypeId", "Jenis dokumen tidak ditemukan."));
        if (!type.isActive()) {
            throw ApiException.validation("documentTypeId", "Jenis dokumen tidak aktif.");
        }
        DocumentClassification classification = classificationRepository.findById(classificationId)
                .orElseThrow(() -> ApiException.validation("classificationId", "Klasifikasi tidak ditemukan."));
        if (!classification.isActive()) {
            throw ApiException.validation("classificationId", "Klasifikasi tidak aktif.");
        }
    }

    private OwnerRef resolveOwner(UUID actor, UUID ownerUserId, UUID ownerOrganizationUnitId) {
        if (ownerUserId != null && ownerOrganizationUnitId != null) {
            throw ApiException.validation("ownerUserId", "Pilih tepat satu pemilik: user atau unit.");
        }
        if (ownerUserId == null && ownerOrganizationUnitId == null) {
            return new OwnerRef(actor, null);
        }
        if (ownerUserId != null) {
            User user = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> ApiException.validation("ownerUserId", "Pemilik user tidak ditemukan."));
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                throw ApiException.validation("ownerUserId", "Pemilik user harus berstatus aktif.");
            }
            return new OwnerRef(ownerUserId, null);
        }
        OrganizationUnit unit = organizationUnitRepository.findById(ownerOrganizationUnitId)
                .orElseThrow(() -> ApiException.validation("ownerOrganizationUnitId", "Unit tidak ditemukan."));
        if (unit.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("ownerOrganizationUnitId", "Unit pemilik harus aktif.");
        }
        return new OwnerRef(null, ownerOrganizationUnitId);
    }

    private void replaceTags(UUID documentId, List<UUID> tagIds, UUID actor) {
        tagAssignmentRepository.deleteAllByIdDocumentId(documentId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new HashSet<>(tagIds);
        List<Tag> tags = tagRepository.findAllById(unique);
        if (tags.size() != unique.size()) {
            throw ApiException.validation("tagIds", "Sebagian tag tidak ditemukan.");
        }
        if (tags.stream().anyMatch(tag -> !tag.isActive())) {
            throw ApiException.validation("tagIds", "Tag tidak aktif tidak dapat dipakai.");
        }
        Instant now = Instant.now(clock);
        tags.forEach(tag -> tagAssignmentRepository.save(
                new DocumentTagAssignment(documentId, tag.getId(), actor, now)));
    }

    private void requireWorkspaceWritable(UUID workspaceId) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        if (workspace.getWorkspaceStatus() == WorkspaceStatus.ARCHIVED) {
            throw ApiException.conflict("Workspace yang diarsipkan bersifat hanya-baca.");
        }
    }

    private Set<UUID> ownerUnitSet(Document document) {
        return document.getOwnerOrganizationUnitId() == null
                ? Set.of()
                : Set.of(document.getOwnerOrganizationUnitId());
    }

    private static DocumentStatus parseStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "Status dokumen tidak dikenal.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record OwnerRef(UUID ownerUserId, UUID ownerOrganizationUnitId) {
    }
}
