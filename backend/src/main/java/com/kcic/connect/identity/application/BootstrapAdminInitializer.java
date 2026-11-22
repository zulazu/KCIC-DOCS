package com.kcic.connect.identity.application;

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
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator only when the user table is empty.
 * The password comes from the environment and must be rotated at first login.
 * After V18 every account must link to an Employee, so the bootstrap admin can
 * be linked at creation time from the configured NIP; an unlinked bootstrap is
 * only viable before the cutover and is logged as such.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String BOOTSTRAP_ROLE = "SYSTEM_ADMIN";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    public BootstrapAdminInitializer(UserRepository userRepository,
                                     EmployeeRepository employeeRepository,
                                     PasswordEncoder passwordEncoder,
                                     AuthorizationService authorizationService,
                                     AuditService auditService,
                                     AppProperties properties,
                                     Clock clock) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        AppProperties.Bootstrap bootstrap = properties.security().bootstrap();
        if (bootstrap == null || bootstrap.username() == null || bootstrap.username().isBlank()
                || bootstrap.password() == null || bootstrap.password().isBlank()) {
            log.warn("Bootstrap admin skipped: KCIC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD are not configured "
                    + "and no users exist. The application has no administrator yet.");
            return;
        }
        int minLength = properties.security().passwordMinLength();
        if (bootstrap.password().length() < minLength) {
            log.error("Bootstrap admin skipped: configured password is shorter than {} characters.", minLength);
            return;
        }
        String employeeNip = blankToNull(bootstrap.employeeNip());
        Employee employee = employeeNip == null ? null : requireActiveEmployee();
        Instant now = Instant.now(clock);
        UUID adminId = UUID.randomUUID();
        User admin = new User(adminId, bootstrap.username().trim(),
                blankToNull(bootstrap.email()), bootstrap.displayName(), passwordEncoder.encode(bootstrap.password()),
                true, now, adminId);
        if (employee != null) {
            admin.linkEmployee(employee.getId(), adminId, now);
        }
        try {
            userRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Bootstrap admin could not be persisted. After V18 every account must "
                    + "link to an ACTIVE Employee: set KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP. Also check that the "
                    + "bootstrap username and email are not already used.", ex);
        }
        authorizationService.assignRoleInternal(adminId, adminId, BOOTSTRAP_ROLE);
        auditService.record(admin.getId(), "BOOTSTRAP_ADMIN_CREATED", "USER", admin.getId(), "SUCCESS",
                Map.of("username", admin.getUsername(), "role", BOOTSTRAP_ROLE,
                        "employeeLinked", employee != null));
        if (employee == null) {
            log.warn("Bootstrap administrator '{}' created without an Employee link. V18 requires every account "
                    + "to link to an ACTIVE Employee; set KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP before the cutover.",
                    admin.getUsername());
        } else {
            log.info("Bootstrap administrator '{}' created with role {} and Employee link. "
                    + "Password change is required at first login.", admin.getUsername(), BOOTSTRAP_ROLE);
        }
    }

    private Employee requireActiveEmployee() {
        Employee employee = employeeRepository.findByNipIgnoreCase(
                        properties.security().bootstrap().employeeNip().trim())
                .orElseThrow(() -> new IllegalStateException("Bootstrap admin cannot be created: the configured "
                        + "KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP does not match any Employee. Import verified HR data "
                        + "first or drop the setting."));
        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalStateException("Bootstrap admin requires an ACTIVE Employee, but the configured "
                    + "KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP belongs to a " + employee.getEmploymentStatus()
                    + " employee.");
        }
        return employee;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
