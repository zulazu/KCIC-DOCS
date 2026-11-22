package com.kcic.connect.identity.application;

import com.kcic.connect.identity.domain.User;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Session principal. Authorization facts are never trusted from the session;
 * they are re-resolved from the database for each protected operation.
 */
public record UserPrincipal(UUID userId, String username, String displayName, boolean mustChangePassword)
        implements Principal, Serializable {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                user.isMustChangePassword());
    }

    public static UserPrincipal require(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.");
        }
        return principal;
    }

    @Override
    public String getName() {
        return username;
    }
}
