package com.kcic.connect.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.shared.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminInitializerTest {

    private static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");
    private static final String PASSWORD = "admin-password-1";

    private UserRepository userRepository;
    private EmployeeRepository employeeRepository;
    private PasswordEncoder passwordEncoder;
    private AuthorizationService authorizationService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        employeeRepository = org.mockito.Mockito.mock(EmployeeRepository.class);
        passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        authorizationService = org.mockito.Mockito.mock(AuthorizationService.class);
        auditService = org.mockito.Mockito.mock(AuditService.class);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
    }

    @Test
    void createsAdminLinkedToActiveEmployeeWhenNipIsConfigured() {
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId, EmploymentStatus.ACTIVE);
        when(employeeRepository.findByNipIgnoreCase("NIP-001")).thenReturn(Optional.of(employee));

        initializer("NIP-001").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmployeeId()).isEqualTo(employeeId);
        verify(authorizationService).assignRoleInternal(saved.getValue().getId(), saved.getValue().getId(),
                "SYSTEM_ADMIN");
    }

    @Test
    void createsUnlinkedAdminWhenNipIsNotConfigured() {
        initializer("  ").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmployeeId()).isNull();
        verify(employeeRepository, never()).findByNipIgnoreCase(any());
    }

    @Test
    void rejectsNonActiveEmployee() {
        when(employeeRepository.findByNipIgnoreCase("NIP-001"))
                .thenReturn(Optional.of(employee(UUID.randomUUID(), EmploymentStatus.INACTIVE)));

        assertThatThrownBy(() -> initializer("NIP-001").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE Employee");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownNip() {
        when(employeeRepository.findByNipIgnoreCase("NIP-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer("NIP-404").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP");
        verify(userRepository, never()).saveAndFlush(any());
    }

    private BootstrapAdminInitializer initializer(String employeeNip) {
        AppProperties properties = new AppProperties(null,
                new AppProperties.Security(false, 12, 5, 15,
                        new AppProperties.Bootstrap("admin", PASSWORD, "admin@kcic.test", "KCIC Admin", employeeNip)),
                null, null);
        return new BootstrapAdminInitializer(userRepository, employeeRepository, passwordEncoder,
                authorizationService, auditService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Employee employee(UUID id, EmploymentStatus status) {
        return new Employee(id, "NIP-001", "Employee Name", "employee@kcic.test", status, null, UUID.randomUUID(), NOW);
    }
}
