package com.kcic.connect.shared.security;

import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.shared.api.ErrorCode;
import com.kcic.connect.shared.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   UserRepository userRepository,
                                                   JsonErrorWriter errorWriter,
                                                   AppProperties properties) throws Exception {
        AccountStateFilter accountStateFilter = new AccountStateFilter(userRepository,
                securityContextRepository, errorWriter);
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(properties.security().cookieSecure()));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/version", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(request,
                                response, ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan."))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                errorWriter.write(request, response, ErrorCode.CSRF,
                                        "Token keamanan tidak valid. Muat ulang halaman.");
                            } else {
                                errorWriter.write(request, response, ErrorCode.ACTION_FORBIDDEN,
                                        "Anda tidak memiliki izin untuk tindakan ini.");
                            }
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterAfter(accountStateFilter, SecurityContextHolderFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }
}
