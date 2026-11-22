package com.kcic.connect.repository.application;

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
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.api.DocumentDtos.DocumentDetailResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentPermissions;
import com.kcic.connect.repository.api.DocumentDtos.DocumentSummaryResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentVersionResponse;
import com.kcic.connect.repository.api.DocumentDtos.OwnerHistoryResponse;
import com.kcic.connect.repository.api.DocumentDtos.TagView;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.DocumentOwnerHistory;
import com.kcic.connect.repository.domain.DocumentTagAssignment;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.ExternalResource;
import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.domain.StorageObject;
import com.kcic.connect.repository.domain.VersionStatus;
import com.kcic.connect.repository.domain.Workspace;
import com.kcic.connect.repository.infrastructure.DocumentTagAssignmentRepository;
import com.kcic.connect.repository.infrastructure.DocumentVersionRepository;
import com.kcic.connect.repository.infrastructure.ExternalResourceRepository;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import com.kcic.connect.repository.infrastructure.StorageObjectRepository;
import com.kcic.connect.repository.infrastructure.WorkspaceRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DocumentResponseMapper {

    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final DocumentTypeRepository typeRepository;
    private final DocumentClassificationRepository classificationRepository;
    private final TagRepository tagRepository;
    private final DocumentTagAssignmentRepository tagAssignmentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final ExternalResourceRepository externalResourceRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserRepository userRepository;

    public DocumentResponseMapper(WorkspaceRepository workspaceRepository,
                                  FolderRepository folderRepository,
                                  DocumentCategoryRepository categoryRepository,
                                  DocumentTypeRepository typeRepository,
                                  DocumentClassificationRepository classificationRepository,
                                  TagRepository tagRepository,
                                  DocumentTagAssignmentRepository tagAssignmentRepository,
                                  DocumentVersionRepository versionRepository,
                                  StorageObjectRepository storageObjectRepository,
                                  ExternalResourceRepository externalResourceRepository,
                                  OrganizationUnitRepository organizationUnitRepository,
                                  UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.folderRepository = folderRepository;
        this.categoryRepository = categoryRepository;
        this.typeRepository = typeRepository;
        this.classificationRepository = classificationRepository;
        this.tagRepository = tagRepository;
        this.tagAssignmentRepository = tagAssignmentRepository;
        this.versionRepository = versionRepository;
        this.storageObjectRepository = storageObjectRepository;
        this.externalResourceRepository = externalResourceRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userRepository = userRepository;
    }

    public List<DocumentSummaryResponse> summaries(List<Document> documents, Set<UUID> favoriteDocumentIds) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = documents.stream().map(Document::getId).toList();
        Map<UUID, Workspace> workspaces = byId(workspaceRepository.findAllById(
                documents.stream().map(Document::getWorkspaceId).collect(Collectors.toSet())));
        Map<UUID, Folder> folders = byId(folderRepository.findAllById(
                documents.stream().map(Document::getFolderId).filter(Objects::nonNull)
                        .collect(Collectors.toSet())));
        Map<UUID, DocumentCategory> categories = byId(categoryRepository.findAllById(
                documents.stream().map(Document::getCategoryId).collect(Collectors.toSet())));
        Map<UUID, DocumentType> types = byId(typeRepository.findAllById(
                documents.stream().map(Document::getDocumentTypeId).collect(Collectors.toSet())));
        Map<UUID, DocumentClassification> classifications = byId(classificationRepository.findAllById(
                documents.stream().map(Document::getClassificationId).collect(Collectors.toSet())));
        Map<UUID, String> unitNames = organizationUnitRepository.findAllById(
                        documents.stream().map(Document::getOwnerOrganizationUnitId).filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OrganizationUnit::getId, OrganizationUnit::getUnitName));
        Map<UUID, String> userNames = userNames(documents.stream().map(Document::getOwnerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, DocumentVersion> currentVersions = versionRepository
                .findAllByDocumentIdInAndVersionStatus(ids, VersionStatus.CURRENT).stream()
                .collect(Collectors.toMap(DocumentVersion::getDocumentId, Function.identity()));
        Map<UUID, List<DocumentTagAssignment>> tagAssignments = tagAssignmentRepository
                .findAllByIdDocumentIdIn(ids).stream()
                .collect(Collectors.groupingBy(DocumentTagAssignment::getDocumentId));
        Map<UUID, Tag> tags = byId(tagRepository.findAllById(tagAssignments.values().stream()
                .flatMap(list -> list.stream().map(DocumentTagAssignment::getTagId)).collect(Collectors.toSet())));

        return documents.stream()
                .map(document -> {
                    Workspace workspace = workspaces.get(document.getWorkspaceId());
                    Folder folder = document.getFolderId() == null ? null : folders.get(document.getFolderId());
                    DocumentCategory category = categories.get(document.getCategoryId());
                    DocumentType type = types.get(document.getDocumentTypeId());
                    DocumentClassification classification = classifications.get(document.getClassificationId());
                    DocumentVersion current = currentVersions.get(document.getId());
                    List<TagView> tagViews = tagAssignments.getOrDefault(document.getId(), List.of()).stream()
                            .map(assignment -> tags.get(assignment.getTagId()))
                            .filter(Objects::nonNull)
                            .map(tag -> new TagView(tag.getId(), tag.getTagCode(), tag.getTagName()))
                            .toList();
                    return new DocumentSummaryResponse(document.getId(), document.getDocumentCode(),
                            document.getTitle(), document.getDocumentStatus().name(), document.getWorkspaceId(),
                            workspace == null ? null : workspace.getWorkspaceName(), document.getFolderId(),
                            folder == null ? null : folder.getFolderName(), document.getCategoryId(),
                            category == null ? null : category.getCategoryName(), document.getDocumentTypeId(),
                            type == null ? null : type.getTypeName(), document.getClassificationId(),
                            classification == null ? null : classification.getClassificationName(),
                            classification == null ? 0 : classification.getSensitivityRank(),
                            document.getOwnerUserId(), nameFor(userNames, document.getOwnerUserId()),
                            document.getOwnerOrganizationUnitId(),
                            nameFor(unitNames, document.getOwnerOrganizationUnitId()),
                            tagViews,
                            current == null ? null : current.getVersionNumber(),
                            current == null ? null : current.getVersionLabel(),
                            current == null ? null : current.getContentSource().name(),
                            document.getUpdatedAt(), favoriteDocumentIds.contains(document.getId()));
                })
                .toList();
    }

    public DocumentSummaryResponse summary(Document document, boolean favorited) {
        return summaries(List.of(document), favorited ? Set.of(document.getId()) : Set.of()).get(0);
    }

    public DocumentVersionResponse version(DocumentVersion version, boolean canPreview, boolean canDownload) {
        String createdByName = userNames(Set.of(version.getCreatedBy())).get(version.getCreatedBy());
        String originalFilename = null;
        String contentType = null;
        Long sizeBytes = null;
        String checksum = null;
        String externalUrl = null;
        String externalHost = null;
        if (version.getStorageObjectId() != null) {
            StorageObject object = storageObjectRepository.findById(version.getStorageObjectId()).orElse(null);
            if (object != null) {
                originalFilename = object.getOriginalFilename();
                contentType = object.getContentType();
                sizeBytes = object.getSizeBytes();
                checksum = object.getChecksumSha256();
            }
        }
        if (version.getExternalResourceId() != null) {
            ExternalResource resource = externalResourceRepository.findById(version.getExternalResourceId())
                    .orElse(null);
            if (resource != null) {
                externalUrl = resource.getUrl();
                externalHost = resource.getNormalizedHost();
            }
        }
        return new DocumentVersionResponse(version.getId(), version.getVersionNumber(), version.getVersionLabel(),
                version.getContentSource().name(), version.getVersionStatus().name(), version.getChangeSummary(),
                version.getCreatedBy(), createdByName, version.getCreatedAt(), version.getPublishedAt(),
                originalFilename, contentType, sizeBytes, checksum, externalUrl, externalHost,
                canPreview, canDownload);
    }

    public List<DocumentVersionResponse> versions(List<DocumentVersion> versions, boolean canPreview,
                                                  boolean canDownload) {
        return versions.stream().map(version -> version(version, canPreview, canDownload)).toList();
    }

    public DocumentDetailResponse detail(Document document, DocumentSummaryResponse summary,
                                         List<DocumentVersion> versions, List<DocumentOwnerHistory> ownerHistory,
                                         DocumentPermissions permissions) {
        Set<UUID> versionCreatorIds = versions.stream().map(DocumentVersion::getCreatedBy)
                .collect(Collectors.toSet());
        Map<UUID, String> creators = userNames(versionCreatorIds);
        Map<UUID, String> unitNames = organizationUnitRepository.findAllById(ownerHistory.stream()
                        .map(DocumentOwnerHistory::getOwnerOrganizationUnitId).filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OrganizationUnit::getId, OrganizationUnit::getUnitName));
        Set<UUID> historyUserIds = new HashSet<>();
        ownerHistory.forEach(history -> {
            if (history.getOwnerUserId() != null) {
                historyUserIds.add(history.getOwnerUserId());
            }
            if (history.getChangedBy() != null) {
                historyUserIds.add(history.getChangedBy());
            }
        });
        Map<UUID, String> historyUsers = userNames(historyUserIds);
        List<OwnerHistoryResponse> historyResponses = ownerHistory.stream()
                .map(history -> new OwnerHistoryResponse(history.getId(), history.getOwnerUserId(),
                        nameFor(historyUsers, history.getOwnerUserId()), history.getOwnerOrganizationUnitId(),
                        nameFor(unitNames, history.getOwnerOrganizationUnitId()), history.getChangedBy(),
                        nameFor(historyUsers, history.getChangedBy()), history.getChangeReason(),
                        history.getValidFrom(), history.getValidUntil()))
                .toList();
        DocumentVersion current = versions.stream()
                .filter(version -> version.getVersionStatus() == VersionStatus.CURRENT)
                .findFirst().orElse(null);
        DocumentVersionResponse currentResponse = current == null ? null
                : version(current, permissions.canPreview(), permissions.canDownload());
        String createdByName = userNames(Set.of(document.getCreatedBy())).get(document.getCreatedBy());
        return new DocumentDetailResponse(summary, document.getDescription(), document.getEffectiveDate(),
                document.getReviewDate(), document.getExpiryDate(), document.getCreatedAt(),
                document.getCreatedBy(), createdByName, currentResponse, historyResponses, versions.size(),
                permissions);
    }

    public Map<UUID, String> userNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    }

    // Immutable maps reject null keys, and a unit-owned document has no owner user id.
    private static String nameFor(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private static <T> Map<UUID, T> byId(List<T> items) {
        return items.stream().collect(Collectors.toMap(item -> {
            if (item instanceof Workspace workspace) {
                return workspace.getId();
            }
            if (item instanceof Folder folder) {
                return folder.getId();
            }
            if (item instanceof DocumentCategory category) {
                return category.getId();
            }
            if (item instanceof DocumentType type) {
                return type.getId();
            }
            if (item instanceof DocumentClassification classification) {
                return classification.getId();
            }
            if (item instanceof Tag tag) {
                return tag.getId();
            }
            throw new IllegalArgumentException("Unsupported lookup type");
        }, Function.identity()));
    }
}
