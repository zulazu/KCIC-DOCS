package com.kcic.connect.repository.api;

import java.util.List;
import java.util.UUID;

public final class DirectoryDtos {

    public record PrincipalOption(UUID id, String name, String detail) {
    }

    public record DirectoryResponse(List<PrincipalOption> users, List<PrincipalOption> roles,
                                    List<PrincipalOption> units) {
    }

    private DirectoryDtos() {
    }
}
