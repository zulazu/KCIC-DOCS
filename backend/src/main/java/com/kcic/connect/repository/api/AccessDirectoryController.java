package com.kcic.connect.repository.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.repository.api.DirectoryDtos.DirectoryResponse;
import com.kcic.connect.repository.application.AccessDirectoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessDirectoryController {

    private final AccessDirectoryService directoryService;

    public AccessDirectoryController(AccessDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/api/v1/access-directory")
    public DirectoryResponse lookup(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam(required = false) String search) {
        return directoryService.lookup(UserPrincipal.require(principal).userId(), search);
    }
}
