package com.kcic.connect.shared.security;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.shared.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-validates the account on every request so deactivation takes effect
 * immediately and authorization facts are never trusted from a stale session.
 * Registered only inside the security filter chain (not as a servlet filter bean).
 */
public class AccountStateFilter extends OncePerRequestFilter {

    private static final List<String> AUTH_SELF_SERVICE_PREFIXES = List.of(
            "/api/v1/auth/",
            "/actuator/health",
            "/api/v1/version");

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final JsonErrorWriter errorWriter;

    public AccountStateFilter(UserRepository userRepository,
                              SecurityContextRepository securityContextRepository,
                              JsonErrorWriter errorWriter) {
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        User user = userRepository.findById(principal.userId()).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, ErrorCode.UNAUTHENTICATED,
                    "Sesi tidak valid. Silakan login kembali.");
            return;
        }
        if (user.isMustChangePassword() && !isSelfServicePath(request)) {
            errorWriter.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Anda harus mengganti password terlebih dahulu.");
            return;
        }
        UserPrincipal refreshed = UserPrincipal.from(user);
        if (!refreshed.equals(principal)) {
            UsernamePasswordAuthenticationToken updated = new UsernamePasswordAuthenticationToken(
                    refreshed, authentication.getCredentials(), authentication.getAuthorities());
            updated.setDetails(authentication.getDetails());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(updated);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private boolean isSelfServicePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return AUTH_SELF_SERVICE_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
