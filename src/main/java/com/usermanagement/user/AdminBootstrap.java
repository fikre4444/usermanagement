package com.usermanagement.user;

import com.usermanagement.audit.AuditLog;
import com.usermanagement.usertype.Actor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Creates the first administrator so a fresh installation can be managed. Runs after role seeding. */
@Component
@Order(20)
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final RegistrationService registrationService;
    private final UserLookup lookup;
    private final AuditLog auditLog;

    AdminBootstrap(BootstrapAdminProperties properties, RegistrationService registrationService, UserLookup lookup,
                   AuditLog auditLog) {
        this.properties = properties;
        this.registrationService = registrationService;
        this.lookup = lookup;
        this.auditLog = auditLog;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.warn("No bootstrap administrator configured (set ADMIN_EMAIL and ADMIN_PASSWORD)");
            return;
        }
        if (lookup.findByIdentifier(properties.email()).isPresent()) {
            return;
        }
        try {
            User admin = registrationService.register(new RegistrationCommand(properties.userType(), properties.username(),
                    properties.email(), null, properties.password(), "System", "Administrator", Map.of(),
                    properties.roles()), Actor.ADMIN);
            auditLog.success("system.admin-bootstrapped", AuditLog.target("user", admin.getId()),
                    Map.of("email", properties.email(), "roles", properties.roles()));
            log.info("Bootstrap administrator {} created", properties.email());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Bootstrap administrator was created concurrently");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
