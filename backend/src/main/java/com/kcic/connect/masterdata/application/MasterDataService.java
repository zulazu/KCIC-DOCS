package com.kcic.connect.masterdata.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.masterdata.api.MasterDataDtos.CategoryResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.ClassificationResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateCategoryRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateClassificationRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreatePositionRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateTagRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateTypeRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.FilePolicyResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.PositionResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.TagResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.TypeResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateCategoryRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateClassificationRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateFilePolicyRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdatePositionRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateTagRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateTypeRequest;
import com.kcic.connect.masterdata.domain.DocumentCategory;
import com.kcic.connect.masterdata.domain.DocumentClassification;
import com.kcic.connect.masterdata.domain.DocumentType;
import com.kcic.connect.masterdata.domain.FilePolicy;
import com.kcic.connect.masterdata.domain.Position;
import com.kcic.connect.masterdata.domain.Tag;
import com.kcic.connect.masterdata.infrastructure.DocumentCategoryRepository;
import com.kcic.connect.masterdata.infrastructure.DocumentClassificationRepository;
import com.kcic.connect.masterdata.infrastructure.DocumentTypeRepository;
import com.kcic.connect.masterdata.infrastructure.FilePolicyRepository;
import com.kcic.connect.masterdata.infrastructure.PositionRepository;
import com.kcic.connect.masterdata.infrastructure.TagRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataService {

    private final DocumentCategoryRepository categoryRepository;
    private final DocumentTypeRepository typeRepository;
    private final DocumentClassificationRepository classificationRepository;
    private final TagRepository tagRepository;
    private final PositionRepository positionRepository;
    private final FilePolicyRepository filePolicyRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public MasterDataService(DocumentCategoryRepository categoryRepository,
                             DocumentTypeRepository typeRepository,
                             DocumentClassificationRepository classificationRepository,
                             TagRepository tagRepository,
                             PositionRepository positionRepository,
                             FilePolicyRepository filePolicyRepository,
                             AuthorizationService authorizationService,
                             AuditService auditService,
                             Clock clock) {
        this.categoryRepository = categoryRepository;
        this.typeRepository = typeRepository;
        this.classificationRepository = classificationRepository;
        this.tagRepository = tagRepository;
        this.positionRepository = positionRepository;
        this.filePolicyRepository = filePolicyRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------- lookups

    @Transactional(readOnly = true)
    public List<CategoryResponse> activeCategories() {
        return categoryRepository.findAllByActiveTrueOrderByCategoryNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TypeResponse> activeTypes() {
        return typeRepository.findAllByActiveTrueOrderByTypeNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClassificationResponse> activeClassifications() {
        return classificationRepository.findAllByActiveTrueOrderBySensitivityRankAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> activeTags() {
        return tagRepository.findAllByActiveTrueOrderByTagNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> activePositions() {
        return positionRepository.findAllByActiveTrueOrderByPositionNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FilePolicyResponse defaultFilePolicy() {
        FilePolicy policy = filePolicyRepository.findFirstByDefaultPolicyTrueAndActiveTrue()
                .orElseThrow(() -> ApiException.notFound("File policy default belum dikonfigurasi."));
        return toResponse(policy);
    }

    // --------------------------------------------------------- admin lists

    @Transactional(readOnly = true)
    public List<CategoryResponse> allCategories(UUID actor) {
        requireManager(actor);
        return categoryRepository.findAllByOrderByCategoryNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TypeResponse> allTypes(UUID actor) {
        requireManager(actor);
        return typeRepository.findAllByOrderByTypeNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClassificationResponse> allClassifications(UUID actor) {
        requireManager(actor);
        return classificationRepository.findAllByOrderBySensitivityRankAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> allTags(UUID actor) {
        requireManager(actor);
        return tagRepository.findAllByOrderByTagNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> allPositions(UUID actor) {
        requireManager(actor);
        return positionRepository.findAllByOrderByPositionNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FilePolicyResponse> allFilePolicies(UUID actor) {
        requireManager(actor);
        return filePolicyRepository.findAllByOrderByPolicyNameAsc().stream()
                .map(MasterDataService::toResponse).toList();
    }

    // ----------------------------------------------------------- categories

    @Transactional
    public CategoryResponse createCategory(UUID actor, CreateCategoryRequest request) {
        requireManager(actor);
        String code = request.categoryCode().trim();
        if (categoryRepository.existsByCategoryCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode kategori sudah digunakan.");
        }
        validateParent(null, request.parentId());
        Instant now = Instant.now(clock);
        DocumentCategory category = categoryRepository.save(new DocumentCategory(UUID.randomUUID(),
                request.parentId(), code, request.categoryName().trim(), request.description(), actor, now));
        auditService.record(actor, "DOCUMENT_CATEGORY_CREATED", "DOCUMENT_CATEGORY", category.getId(), "SUCCESS",
                Map.of("categoryCode", code));
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID actor, UUID categoryId, UpdateCategoryRequest request) {
        requireManager(actor);
        DocumentCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Kategori tidak ditemukan."));
        validateParent(categoryId, request.parentId());
        category.update(request.parentId(), request.categoryName().trim(), request.description(),
                request.active(), actor, Instant.now(clock));
        auditService.record(actor, "DOCUMENT_CATEGORY_UPDATED", "DOCUMENT_CATEGORY", categoryId, "SUCCESS",
                Map.of("categoryCode", category.getCategoryCode(), "active", request.active()));
        return toResponse(category);
    }

    private void validateParent(UUID categoryId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(categoryId)) {
            throw ApiException.validation("parentId", "Kategori tidak boleh menjadi induk dirinya sendiri.");
        }
        DocumentCategory parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> ApiException.validation("parentId", "Kategori induk tidak ditemukan."));
        if (!parent.isActive()) {
            throw ApiException.validation("parentId", "Kategori induk harus aktif.");
        }
        Set<UUID> seen = new HashSet<>();
        UUID cursor = parentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(categoryId)) {
                throw ApiException.validation("parentId", "Hierarki kategori tidak boleh melingkar.");
            }
            if (!seen.add(cursor) || depth++ > 100) {
                throw ApiException.validation("parentId", "Hierarki kategori tidak valid.");
            }
            DocumentCategory current = categoryRepository.findById(cursor).orElse(null);
            cursor = current == null ? null : current.getParentId();
        }
    }

    // --------------------------------------------------------------- types

    @Transactional
    public TypeResponse createType(UUID actor, CreateTypeRequest request) {
        requireManager(actor);
        String code = request.typeCode().trim();
        if (typeRepository.existsByTypeCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode tipe sudah digunakan.");
        }
        DocumentType type = typeRepository.save(new DocumentType(UUID.randomUUID(), code,
                request.typeName().trim(), request.description(), actor, Instant.now(clock)));
        auditService.record(actor, "DOCUMENT_TYPE_CREATED", "DOCUMENT_TYPE", type.getId(), "SUCCESS",
                Map.of("typeCode", code));
        return toResponse(type);
    }

    @Transactional
    public TypeResponse updateType(UUID actor, UUID typeId, UpdateTypeRequest request) {
        requireManager(actor);
        DocumentType type = typeRepository.findById(typeId)
                .orElseThrow(() -> ApiException.notFound("Tipe dokumen tidak ditemukan."));
        type.update(request.typeName().trim(), request.description(), request.active(), actor,
                Instant.now(clock));
        auditService.record(actor, "DOCUMENT_TYPE_UPDATED", "DOCUMENT_TYPE", typeId, "SUCCESS",
                Map.of("typeCode", type.getTypeCode(), "active", request.active()));
        return toResponse(type);
    }

    // ------------------------------------------------------ classifications

    @Transactional
    public ClassificationResponse createClassification(UUID actor, CreateClassificationRequest request) {
        requireManager(actor);
        String code = request.classificationCode().trim();
        if (classificationRepository.existsByClassificationCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode klasifikasi sudah digunakan.");
        }
        DocumentClassification classification = classificationRepository.save(new DocumentClassification(
                UUID.randomUUID(), code, request.classificationName().trim(), request.sensitivityRank(),
                request.description(), actor, Instant.now(clock)));
        auditService.record(actor, "DOCUMENT_CLASSIFICATION_CREATED", "DOCUMENT_CLASSIFICATION",
                classification.getId(), "SUCCESS", Map.of("classificationCode", code,
                        "sensitivityRank", request.sensitivityRank()));
        return toResponse(classification);
    }

    @Transactional
    public ClassificationResponse updateClassification(UUID actor, UUID classificationId,
                                                      UpdateClassificationRequest request) {
        requireManager(actor);
        DocumentClassification classification = classificationRepository.findById(classificationId)
                .orElseThrow(() -> ApiException.notFound("Klasifikasi tidak ditemukan."));
        classification.update(request.classificationName().trim(), request.sensitivityRank(),
                request.description(), request.active(), actor, Instant.now(clock));
        auditService.record(actor, "DOCUMENT_CLASSIFICATION_UPDATED", "DOCUMENT_CLASSIFICATION",
                classificationId, "SUCCESS", Map.of("classificationCode", classification.getClassificationCode(),
                        "sensitivityRank", request.sensitivityRank(), "active", request.active()));
        return toResponse(classification);
    }

    // ---------------------------------------------------------------- tags

    @Transactional
    public TagResponse createTag(UUID actor, CreateTagRequest request) {
        requireManager(actor);
        String code = request.tagCode().trim();
        if (tagRepository.existsByTagCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode tag sudah digunakan.");
        }
        Tag tag = tagRepository.save(new Tag(UUID.randomUUID(), code, request.tagName().trim(),
                Instant.now(clock)));
        auditService.record(actor, "TAG_CREATED", "TAG", tag.getId(), "SUCCESS", Map.of("tagCode", code));
        return toResponse(tag);
    }

    @Transactional
    public TagResponse updateTag(UUID actor, UUID tagId, UpdateTagRequest request) {
        requireManager(actor);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> ApiException.notFound("Tag tidak ditemukan."));
        tag.update(request.tagName().trim(), request.active(), Instant.now(clock));
        auditService.record(actor, "TAG_UPDATED", "TAG", tagId, "SUCCESS",
                Map.of("tagCode", tag.getTagCode(), "active", request.active()));
        return toResponse(tag);
    }

    // ----------------------------------------------------------- positions

    @Transactional
    public PositionResponse createPosition(UUID actor, CreatePositionRequest request) {
        requireManager(actor);
        String code = request.positionCode().trim();
        if (positionRepository.existsByPositionCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode jabatan sudah digunakan.");
        }
        Position position = positionRepository.save(new Position(UUID.randomUUID(), code,
                request.positionName().trim(), request.description(), actor, Instant.now(clock)));
        auditService.record(actor, "POSITION_CREATED", "POSITION", position.getId(), "SUCCESS",
                Map.of("positionCode", code));
        return toResponse(position);
    }

    @Transactional
    public PositionResponse updatePosition(UUID actor, UUID positionId, UpdatePositionRequest request) {
        requireManager(actor);
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> ApiException.notFound("Jabatan tidak ditemukan."));
        position.update(request.positionName().trim(), request.description(), request.active(), actor,
                Instant.now(clock));
        auditService.record(actor, "POSITION_UPDATED", "POSITION", positionId, "SUCCESS",
                Map.of("positionCode", position.getPositionCode(), "active", request.active()));
        return toResponse(position);
    }

    // --------------------------------------------------------- file policy

    @Transactional
    public FilePolicyResponse updateFilePolicy(UUID actor, UUID policyId, UpdateFilePolicyRequest request) {
        requireManager(actor);
        FilePolicy policy = filePolicyRepository.findById(policyId)
                .orElseThrow(() -> ApiException.notFound("File policy tidak ditemukan."));
        Set<String> extensions = new LinkedHashSet<>();
        for (String extension : request.allowedExtensions()) {
            extensions.add(extension.trim().toLowerCase());
        }
        Set<String> mimeTypes = new LinkedHashSet<>();
        for (String mimeType : request.allowedMimeTypes()) {
            mimeTypes.add(mimeType.trim().toLowerCase());
        }
        if (extensions.isEmpty() || mimeTypes.isEmpty()) {
            throw ApiException.validation("allowedExtensions", "Allowlist file tidak boleh kosong.");
        }
        policy.update(List.copyOf(extensions), List.copyOf(mimeTypes), request.maxFileSizeBytes(), actor,
                Instant.now(clock));
        auditService.record(actor, "FILE_POLICY_UPDATED", "FILE_POLICY", policyId, "SUCCESS",
                Map.of("policyCode", policy.getPolicyCode(), "extensions", extensions.size(),
                        "mimeTypes", mimeTypes.size(), "maxFileSizeBytes", request.maxFileSizeBytes()));
        return toResponse(policy);
    }

    private void requireManager(UUID actor) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.MASTER_DATA_MANAGE);
    }

    private static CategoryResponse toResponse(DocumentCategory category) {
        return new CategoryResponse(category.getId(), category.getParentId(), category.getCategoryCode(),
                category.getCategoryName(), category.getDescription(), category.isActive(),
                category.getCreatedAt(), category.getUpdatedAt());
    }

    private static TypeResponse toResponse(DocumentType type) {
        return new TypeResponse(type.getId(), type.getTypeCode(), type.getTypeName(), type.getDescription(),
                type.isActive(), type.getCreatedAt(), type.getUpdatedAt());
    }

    private static ClassificationResponse toResponse(DocumentClassification classification) {
        return new ClassificationResponse(classification.getId(), classification.getClassificationCode(),
                classification.getClassificationName(), classification.getSensitivityRank(),
                classification.getDescription(), classification.isActive(), classification.getCreatedAt(),
                classification.getUpdatedAt());
    }

    private static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getTagCode(), tag.getTagName(), tag.isActive(),
                tag.getCreatedAt(), tag.getUpdatedAt());
    }

    private static PositionResponse toResponse(Position position) {
        return new PositionResponse(position.getId(), position.getPositionCode(), position.getPositionName(),
                position.getDescription(), position.isActive(), position.getCreatedAt(), position.getUpdatedAt());
    }

    private static FilePolicyResponse toResponse(FilePolicy policy) {
        return new FilePolicyResponse(policy.getId(), policy.getPolicyCode(), policy.getPolicyName(),
                policy.getAllowedExtensions(), policy.getAllowedMimeTypes(), policy.getMaxFileSizeBytes(),
                policy.isDefaultPolicy(), policy.isActive(), policy.getUpdatedAt());
    }
}
