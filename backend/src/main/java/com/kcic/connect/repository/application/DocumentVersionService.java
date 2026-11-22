package com.kcic.connect.repository.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.masterdata.domain.FilePolicy;
import com.kcic.connect.repository.api.DocumentDtos.DocumentVersionResponse;
import com.kcic.connect.repository.api.DocumentDtos.ExternalLinkResponse;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.ExternalResource;
import com.kcic.connect.repository.domain.StorageObject;
import com.kcic.connect.repository.domain.StorageObjectStatus;
import com.kcic.connect.repository.domain.VersionStatus;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.repository.infrastructure.DocumentVersionRepository;
import com.kcic.connect.repository.infrastructure.ExternalResourceRepository;
import com.kcic.connect.repository.infrastructure.StorageObjectRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentVersionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVersionService.class);

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final ExternalResourceRepository externalResourceRepository;
    private final DocumentAccessService accessService;
    private final DocumentResponseMapper mapper;
    private final FileValidationService fileValidationService;
    private final StorageAdapter storageAdapter;
    private final WorkspaceService workspaceService;
    private final AppProperties properties;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DocumentVersionService(DocumentService documentService,
                                  DocumentRepository documentRepository,
                                  DocumentVersionRepository versionRepository,
                                  StorageObjectRepository storageObjectRepository,
                                  ExternalResourceRepository externalResourceRepository,
                                  DocumentAccessService accessService,
                                  DocumentResponseMapper mapper,
                                  FileValidationService fileValidationService,
                                  StorageAdapter storageAdapter,
                                  WorkspaceService workspaceService,
                                  AppProperties properties,
                                  AuditService auditService,
                                  Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.storageObjectRepository = storageObjectRepository;
        this.externalResourceRepository = externalResourceRepository;
        this.accessService = accessService;
        this.mapper = mapper;
        this.fileValidationService = fileValidationService;
        this.storageAdapter = storageAdapter;
        this.workspaceService = workspaceService;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public DocumentVersionResponse upload(UUID actor, UUID documentId, MultipartFile file, String versionLabel,
                                          String changeSummary) {
        Document document = documentService.loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        DocumentService.Grants grants = documentService.grants(document);
        if (!accessService.canUpload(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk mengunggah versi dokumen ini.");
        }
        requireMutable(document);
        boolean canPreview = accessService.canPreview(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        boolean canDownload = accessService.canDownload(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());

        FilePolicy policy = fileValidationService.requirePolicy();
        String sanitizedFilename = fileValidationService.validateMetadata(file.getOriginalFilename(),
                file.getContentType(), file.getSize(), policy);
        String contentType = normalizedContentType(file.getContentType());
        Path temp = null;
        UUID versionId = UUID.randomUUID();
        UUID storageObjectId = UUID.randomUUID();
        String bucket = properties.storage().bucket();
        String objectKey = "documents/" + documentId + "/" + versionId + "/" + UUID.randomUUID();
        try {
            temp = Files.createTempFile("kcic-upload-", ".tmp");
            String checksum = copyToTemp(file, temp, policy.getMaxFileSizeBytes());
            long size = Files.size(temp);
            fileValidationService.verifySignature(sanitizedFilename, temp);
            storageAdapter.put(bucket, objectKey, temp, size, contentType);
            String label = trimToNull(versionLabel);
            String summary = trimToNull(changeSummary);
            DocumentVersionResponse response = transactionTemplate.execute(status ->
                    persistStorageVersion(actor, documentId, versionId, storageObjectId, bucket, objectKey,
                            sanitizedFilename, contentType, size, checksum, label, summary, canPreview,
                            canDownload));
            if (response == null) {
                throw new StorageException("Versi dokumen gagal disimpan.");
            }
            return response;
        } catch (RuntimeException ex) {
            deleteOrRegisterOrphan(bucket, objectKey);
            throw ex;
        } catch (IOException ex) {
            deleteOrRegisterOrphan(bucket, objectKey);
            throw new StorageException("File unggahan tidak dapat diproses.", ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    log.warn("Temporary upload file could not be deleted: {}", temp);
                }
            }
        }
    }

    private DocumentVersionResponse persistStorageVersion(UUID actor, UUID documentId, UUID versionId,
                                                          UUID storageObjectId, String bucket, String objectKey,
                                                          String filename, String contentType, long size,
                                                          String checksum, String label, String summary,
                                                          boolean canPreview, boolean canDownload) {
        Document document = documentRepository.findWithLockById(documentId)
                .orElseThrow(() -> ApiException.notFound("Dokumen tidak ditemukan."));
        if (document.getDocumentStatus() == com.kcic.connect.repository.domain.DocumentStatus.ARCHIVED) {
            throw ApiException.conflict("Dokumen yang diarsipkan tidak dapat menerima versi baru.");
        }
        Instant now = Instant.now(clock);
        int nextNumber = versionRepository.maxVersionNumber(documentId) + 1;
        supersedeCurrentVersions(documentId);
        StorageObject object = storageObjectRepository.save(new StorageObject(storageObjectId,
                storageAdapter.provider(), bucket, objectKey, filename, contentType, size, checksum, actor, now));
        DocumentVersion version = versionRepository.save(new DocumentVersion(versionId, documentId, nextNumber,
                label, object.getId(), null, summary, actor, now));
        document.touch(actor, now);
        auditService.record(actor, "DOCUMENT_VERSION_UPLOADED", "DOCUMENT", documentId, "SUCCESS",
                Map.of("versionNumber", nextNumber, "contentSource", version.getContentSource().name(),
                        "contentType", contentType, "sizeBytes", size));
        return mapper.version(version, canPreview, canDownload);
    }

    @Transactional
    public DocumentVersionResponse registerExternalLink(UUID actor, UUID documentId, String rawUrl,
                                                         String linkTitle, String changeSummary,
                                                         String versionLabel) {
        Document document = documentService.loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        DocumentService.Grants grants = documentService.grants(document);
        if (!accessService.canCreateVersion(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk menambah versi dokumen ini.");
        }
        Document locked = documentRepository.findWithLockById(documentId)
                .orElseThrow(() -> ApiException.notFound("Dokumen tidak ditemukan."));
        if (locked.getDocumentStatus() == com.kcic.connect.repository.domain.DocumentStatus.ARCHIVED) {
            throw ApiException.conflict("Dokumen yang diarsipkan tidak dapat menerima versi baru.");
        }
        URI uri = parseHttpsUrl(rawUrl);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.length() > 255) {
            throw ApiException.validation("url", "Host tautan terlalu panjang.");
        }
        Instant now = Instant.now(clock);
        ExternalResource resource = externalResourceRepository.save(new ExternalResource(UUID.randomUUID(),
                uri.toString(), host, providerCode(host), trimToNull(linkTitle), actor, now));
        int nextNumber = versionRepository.maxVersionNumber(documentId) + 1;
        supersedeCurrentVersions(documentId);
        DocumentVersion version = versionRepository.save(new DocumentVersion(UUID.randomUUID(), documentId,
                nextNumber, trimToNull(versionLabel), null, resource.getId(), trimToNull(changeSummary), actor,
                now));
        locked.touch(actor, now);
        auditService.record(actor, "DOCUMENT_EXTERNAL_LINK_REGISTERED", "DOCUMENT", documentId, "SUCCESS",
                Map.of("versionNumber", nextNumber, "host", host));
        boolean canPreview = accessService.canPreview(ctx, locked, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        boolean canDownload = accessService.canDownload(ctx, locked, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        return mapper.version(version, canPreview, canDownload);
    }

    @Transactional
    public DocumentVersionResponse promote(UUID actor, UUID documentId, UUID versionId) {
        Document document = documentService.loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        DocumentService.Grants grants = documentService.grants(document);
        if (!accessService.canCreateVersion(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk mengubah versi aktif dokumen ini.");
        }
        DocumentVersion version = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> ApiException.notFound("Versi dokumen tidak ditemukan."));
        Instant now = Instant.now(clock);
        if (version.getVersionStatus() == VersionStatus.WITHDRAWN) {
            throw ApiException.conflict("Versi yang ditarik tidak dapat dipromosikan.");
        }
        DocumentVersion current = versionRepository
                .findFirstByDocumentIdAndVersionStatus(documentId, VersionStatus.CURRENT).orElse(null);
        if (current != null && current.getId().equals(version.getId())) {
            boolean canPreview = accessService.canPreview(ctx, document, grants.documentGrants(),
                    grants.folderGrants(), grants.workspaceGrants());
            boolean canDownload = accessService.canDownload(ctx, document, grants.documentGrants(),
                    grants.folderGrants(), grants.workspaceGrants());
            return mapper.version(version, canPreview, canDownload);
        }
        supersedeCurrentVersions(documentId);
        version.promote(now);
        document.touch(actor, now);
        auditService.record(actor, "DOCUMENT_VERSION_PROMOTED", "DOCUMENT", documentId, "SUCCESS",
                Map.of("previousVersionNumber", current == null ? 0 : current.getVersionNumber(),
                        "promotedVersionNumber", version.getVersionNumber()));
        boolean canPreview = accessService.canPreview(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        boolean canDownload = accessService.canDownload(ctx, document, grants.documentGrants(),
                grants.folderGrants(), grants.workspaceGrants());
        return mapper.version(version, canPreview, canDownload);
    }

    public ContentResult openContent(UUID actor, UUID documentId, UUID versionId, boolean download) {
        Document document = documentService.loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        DocumentService.Grants grants = documentService.grants(document);
        boolean allowed = download
                ? accessService.canDownload(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants())
                : accessService.canPreview(ctx, document, grants.documentGrants(), grants.folderGrants(),
                        grants.workspaceGrants());
        if (!allowed) {
            throw ApiException.forbidden(download
                    ? "Anda tidak memiliki izin untuk mengunduh dokumen ini."
                    : "Anda tidak memiliki izin untuk membuka pratinjau dokumen ini.");
        }
        DocumentVersion version = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> ApiException.notFound("Versi dokumen tidak ditemukan."));
        if (version.getStorageObjectId() == null) {
            throw ApiException.conflict("Versi ini berupa tautan eksternal, bukan file tersimpan.");
        }
        StorageObject object = storageObjectRepository.findById(version.getStorageObjectId())
                .orElseThrow(() -> ApiException.conflict("File versi ini tidak tersedia."));
        if (object.getUploadStatus() != StorageObjectStatus.AVAILABLE) {
            throw ApiException.conflict("File versi ini tidak tersedia.");
        }
        InputStream stream = storageAdapter.get(object.getBucketName(), object.getObjectKey());
        auditService.record(actor, download ? "DOCUMENT_DOWNLOADED" : "DOCUMENT_PREVIEWED", "DOCUMENT",
                documentId, "SUCCESS", Map.of("versionNumber", version.getVersionNumber()));
        return new ContentResult(stream, object.getOriginalFilename(), object.getContentType(),
                object.getSizeBytes(), object.getChecksumSha256());
    }

    @Transactional
    public ExternalLinkResponse openExternalLink(UUID actor, UUID documentId, UUID versionId) {
        Document document = documentService.loadDocument(documentId);
        DocumentAccessService.ActorContext ctx = accessService.context(actor);
        DocumentService.Grants grants = documentService.grants(document);
        if (!accessService.canPreview(ctx, document, grants.documentGrants(), grants.folderGrants(),
                grants.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk membuka tautan dokumen ini.");
        }
        DocumentVersion version = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> ApiException.notFound("Versi dokumen tidak ditemukan."));
        if (version.getExternalResourceId() == null) {
            throw ApiException.conflict("Versi ini bukan tautan eksternal.");
        }
        ExternalResource resource = externalResourceRepository.findById(version.getExternalResourceId())
                .orElseThrow(() -> ApiException.conflict("Tautan eksternal tidak tersedia."));
        auditService.record(actor, "DOCUMENT_EXTERNAL_LINK_OPENED", "DOCUMENT", documentId, "SUCCESS",
                Map.of("versionNumber", version.getVersionNumber()));
        return new ExternalLinkResponse(resource.getUrl(), resource.getNormalizedHost(),
                "Tautan ini dibuka di provider asal. Anda mungkin memerlukan izin akses terpisah di layanan tersebut.");
    }

    public record ContentResult(InputStream stream, String filename, String contentType, long sizeBytes,
                                String checksumSha256) {
    }

    private void supersedeCurrentVersions(UUID documentId) {
        List<DocumentVersion> current = versionRepository.findAllByDocumentIdAndVersionStatus(documentId,
                VersionStatus.CURRENT);
        if (current.isEmpty()) {
            return;
        }
        current.forEach(DocumentVersion::supersede);
        // Flush before inserting/promoting the next CURRENT row: the partial unique
        // index on (document_id) WHERE status = 'CURRENT' rejects transient duplicates.
        versionRepository.flush();
    }

    private void requireMutable(Document document) {
        if (document.getDocumentStatus() == com.kcic.connect.repository.domain.DocumentStatus.ARCHIVED) {
            throw ApiException.conflict("Dokumen yang diarsipkan tidak dapat menerima versi baru.");
        }
        if (workspaceService.loadWorkspace(document.getWorkspaceId()).getWorkspaceStatus()
                == com.kcic.connect.repository.domain.WorkspaceStatus.ARCHIVED) {
            throw ApiException.conflict("Workspace yang diarsipkan bersifat hanya-baca.");
        }
    }

    private String copyToTemp(MultipartFile file, Path target, long maxBytes) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
        long total = 0;
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ApiException(com.kcic.connect.shared.api.ErrorCode.FILE_TOO_LARGE,
                            "Ukuran file melebihi batas " + (maxBytes / (1024 * 1024)) + " MB.");
                }
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw ApiException.validation("file", "File kosong tidak dapat diunggah.");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteOrRegisterOrphan(String bucket, String objectKey) {
        try {
            storageAdapter.delete(bucket, objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.error("Orphan storage object requires manual cleanup bucket={} key={}", bucket, objectKey,
                    cleanupFailure);
        }
    }

    private static URI parseHttpsUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw ApiException.validation("url", "URL harus menggunakan HTTPS.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw ApiException.validation("url", "URL harus memiliki host yang valid.");
            }
            if (uri.getUserInfo() != null) {
                throw ApiException.validation("url", "URL tidak boleh memuat kredensial.");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw ApiException.validation("url", "URL tidak valid.");
        }
    }

    private static String providerCode(String host) {
        if (host.endsWith("sharepoint.com")) {
            return "SHAREPOINT";
        }
        if (host.endsWith("onedrive.live.com") || host.endsWith("1drv.ms")) {
            return "ONEDRIVE";
        }
        if (host.equals("docs.google.com") || host.endsWith("drive.google.com")) {
            return "GOOGLE_DRIVE";
        }
        if (host.endsWith("office.com") || host.endsWith("officeapps.live.com")) {
            return "OFFICE";
        }
        return "EXTERNAL";
    }

    private static String normalizedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
