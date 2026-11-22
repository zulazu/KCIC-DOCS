package com.kcic.connect.masterdata.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.masterdata.api.MasterDataDtos.CategoryResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.ClassificationResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateCategoryRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateClassificationRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateTagRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.CreateTypeRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.FilePolicyResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.TagResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.TypeResponse;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateCategoryRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateClassificationRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateFilePolicyRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateTagRequest;
import com.kcic.connect.masterdata.api.MasterDataDtos.UpdateTypeRequest;
import com.kcic.connect.masterdata.application.MasterDataService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    // ---------------------------------------------------- lookup endpoints

    @GetMapping("/api/v1/master-data/categories")
    public List<CategoryResponse> categories() {
        return masterDataService.activeCategories();
    }

    @GetMapping("/api/v1/master-data/document-types")
    public List<TypeResponse> documentTypes() {
        return masterDataService.activeTypes();
    }

    @GetMapping("/api/v1/master-data/classifications")
    public List<ClassificationResponse> classifications() {
        return masterDataService.activeClassifications();
    }

    @GetMapping("/api/v1/master-data/tags")
    public List<TagResponse> tags() {
        return masterDataService.activeTags();
    }

    @GetMapping("/api/v1/master-data/file-policy")
    public FilePolicyResponse defaultFilePolicy() {
        return masterDataService.defaultFilePolicy();
    }

    // ------------------------------------------------------ admin endpoints

    @GetMapping("/api/v1/admin/master-data/categories")
    public List<CategoryResponse> allCategories(@AuthenticationPrincipal UserPrincipal principal) {
        return masterDataService.allCategories(UserPrincipal.require(principal).userId());
    }

    @PostMapping("/api/v1/admin/master-data/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@AuthenticationPrincipal UserPrincipal principal,
                                           @Valid @RequestBody CreateCategoryRequest request) {
        return masterDataService.createCategory(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/admin/master-data/categories/{categoryId}")
    public CategoryResponse updateCategory(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID categoryId,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return masterDataService.updateCategory(UserPrincipal.require(principal).userId(), categoryId, request);
    }

    @GetMapping("/api/v1/admin/master-data/document-types")
    public List<TypeResponse> allTypes(@AuthenticationPrincipal UserPrincipal principal) {
        return masterDataService.allTypes(UserPrincipal.require(principal).userId());
    }

    @PostMapping("/api/v1/admin/master-data/document-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TypeResponse createType(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody CreateTypeRequest request) {
        return masterDataService.createType(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/admin/master-data/document-types/{typeId}")
    public TypeResponse updateType(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID typeId,
                                   @Valid @RequestBody UpdateTypeRequest request) {
        return masterDataService.updateType(UserPrincipal.require(principal).userId(), typeId, request);
    }

    @GetMapping("/api/v1/admin/master-data/classifications")
    public List<ClassificationResponse> allClassifications(@AuthenticationPrincipal UserPrincipal principal) {
        return masterDataService.allClassifications(UserPrincipal.require(principal).userId());
    }

    @PostMapping("/api/v1/admin/master-data/classifications")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassificationResponse createClassification(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateClassificationRequest request) {
        return masterDataService.createClassification(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/admin/master-data/classifications/{classificationId}")
    public ClassificationResponse updateClassification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID classificationId,
            @Valid @RequestBody UpdateClassificationRequest request) {
        return masterDataService.updateClassification(UserPrincipal.require(principal).userId(),
                classificationId, request);
    }

    @GetMapping("/api/v1/admin/master-data/tags")
    public List<TagResponse> allTags(@AuthenticationPrincipal UserPrincipal principal) {
        return masterDataService.allTags(UserPrincipal.require(principal).userId());
    }

    @PostMapping("/api/v1/admin/master-data/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createTag(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody CreateTagRequest request) {
        return masterDataService.createTag(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/admin/master-data/tags/{tagId}")
    public TagResponse updateTag(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable UUID tagId,
                                 @Valid @RequestBody UpdateTagRequest request) {
        return masterDataService.updateTag(UserPrincipal.require(principal).userId(), tagId, request);
    }

    @GetMapping("/api/v1/admin/master-data/file-policies")
    public List<FilePolicyResponse> allFilePolicies(@AuthenticationPrincipal UserPrincipal principal) {
        return masterDataService.allFilePolicies(UserPrincipal.require(principal).userId());
    }

    @PutMapping("/api/v1/admin/master-data/file-policies/{policyId}")
    public FilePolicyResponse updateFilePolicy(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID policyId,
                                               @Valid @RequestBody UpdateFilePolicyRequest request) {
        return masterDataService.updateFilePolicy(UserPrincipal.require(principal).userId(), policyId, request);
    }
}
