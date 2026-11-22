package com.kcic.connect.repository.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.repository.api.DocumentDtos.ActivityResponse;
import com.kcic.connect.repository.api.DocumentDtos.CreateDocumentRequest;
import com.kcic.connect.repository.api.DocumentDtos.DocumentDetailResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentSummaryResponse;
import com.kcic.connect.repository.api.DocumentDtos.DocumentVersionResponse;
import com.kcic.connect.repository.api.DocumentDtos.ExternalLinkRequest;
import com.kcic.connect.repository.api.DocumentDtos.ExternalLinkResponse;
import com.kcic.connect.repository.api.DocumentDtos.UpdateDocumentRequest;
import com.kcic.connect.repository.application.DocumentService;
import com.kcic.connect.repository.application.DocumentVersionService;
import com.kcic.connect.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionService versionService;

    public DocumentController(DocumentService documentService, DocumentVersionService versionService) {
        this.documentService = documentService;
        this.versionService = versionService;
    }

    // ---------------------------------------------------------------- search

    @GetMapping("/api/v1/documents")
    public PageResponse<DocumentSummaryResponse> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID documentTypeId,
            @RequestParam(required = false) UUID classificationId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) UUID ownerUnitId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return documentService.search(UserPrincipal.require(principal).userId(), workspaceId, keyword,
                categoryId, documentTypeId, classificationId, ownerUserId, ownerUnitId, tagId, status,
                includeArchived, page, size, sort);
    }

    @GetMapping("/api/v1/documents/favorites")
    public List<DocumentSummaryResponse> favorites(@AuthenticationPrincipal UserPrincipal principal) {
        return documentService.favorites(UserPrincipal.require(principal).userId());
    }

    // ------------------------------------------------------- document record

    @PostMapping("/api/v1/workspaces/{workspaceId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID workspaceId,
                                         @Valid @RequestBody CreateDocumentRequest request) {
        return documentService.create(UserPrincipal.require(principal).userId(), workspaceId, request);
    }

    @GetMapping("/api/v1/documents/{documentId}")
    public DocumentDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID documentId) {
        return documentService.detail(UserPrincipal.require(principal).userId(), documentId);
    }

    @PutMapping("/api/v1/documents/{documentId}")
    public DocumentDetailResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID documentId,
                                         @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.update(UserPrincipal.require(principal).userId(), documentId, request);
    }

    @PostMapping("/api/v1/documents/{documentId}/activate")
    public DocumentDetailResponse activate(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID documentId) {
        return documentService.activate(UserPrincipal.require(principal).userId(), documentId);
    }

    @PostMapping("/api/v1/documents/{documentId}/archive")
    public DocumentDetailResponse archive(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID documentId) {
        return documentService.archive(UserPrincipal.require(principal).userId(), documentId);
    }

    @PostMapping("/api/v1/documents/{documentId}/restore")
    public DocumentDetailResponse restore(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID documentId) {
        return documentService.restore(UserPrincipal.require(principal).userId(), documentId);
    }

    // -------------------------------------------------------------- versions

    @GetMapping("/api/v1/documents/{documentId}/versions")
    public List<DocumentVersionResponse> versions(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID documentId) {
        return documentService.versions(UserPrincipal.require(principal).userId(), documentId);
    }

    @PostMapping("/api/v1/documents/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentVersionResponse upload(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID documentId,
                                          @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) String versionLabel,
                                          @RequestParam(required = false) String changeSummary) {
        return versionService.upload(UserPrincipal.require(principal).userId(), documentId, file, versionLabel,
                changeSummary);
    }

    @PostMapping("/api/v1/documents/{documentId}/versions/external-link")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentVersionResponse registerExternalLink(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID documentId,
                                                        @Valid @RequestBody ExternalLinkRequest request) {
        return versionService.registerExternalLink(UserPrincipal.require(principal).userId(), documentId,
                request.url(), request.linkTitle(), request.changeSummary(), request.versionLabel());
    }

    @PostMapping("/api/v1/documents/{documentId}/versions/{versionId}/promote")
    public DocumentVersionResponse promote(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID documentId,
                                           @PathVariable UUID versionId) {
        return versionService.promote(UserPrincipal.require(principal).userId(), documentId, versionId);
    }

    @GetMapping("/api/v1/documents/{documentId}/versions/{versionId}/content")
    public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID documentId,
                                                       @PathVariable UUID versionId,
                                                       @RequestParam(defaultValue = "false")
                                                       boolean download) {
        DocumentVersionService.ContentResult content = versionService.openContent(
                UserPrincipal.require(principal).userId(), documentId, versionId, download);
        String disposition = ContentDisposition
                .builder(download ? "attachment" : "inline")
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(mediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.stream()));
    }

    @GetMapping("/api/v1/documents/{documentId}/versions/{versionId}/external-link")
    public ExternalLinkResponse externalLink(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID documentId,
                                             @PathVariable UUID versionId) {
        return versionService.openExternalLink(UserPrincipal.require(principal).userId(), documentId, versionId);
    }

    // -------------------------------------------------------------- activity

    @GetMapping("/api/v1/documents/{documentId}/activity")
    public List<ActivityResponse> activity(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID documentId) {
        return documentService.activity(UserPrincipal.require(principal).userId(), documentId);
    }

    // ------------------------------------------------------------- favorites

    @PutMapping("/api/v1/documents/{documentId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID documentId) {
        documentService.addFavorite(UserPrincipal.require(principal).userId(), documentId);
    }

    @DeleteMapping("/api/v1/documents/{documentId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID documentId) {
        documentService.removeFavorite(UserPrincipal.require(principal).userId(), documentId);
    }

    private static MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
