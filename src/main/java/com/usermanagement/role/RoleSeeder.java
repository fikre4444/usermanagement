package com.usermanagement.role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Creates the roles declared under {@code app.roles} when the application starts. */
@Component
@Order(10)
class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private final RoleProperties properties;
    private final RoleService roleService;

    RoleSeeder(RoleProperties properties, RoleService roleService) {
        this.properties = properties;
        this.roleService = roleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.roles().forEach((name, definition) -> {
            try {
                roleService.syncConfiguredRole(name, definition);
            } catch (DataIntegrityViolationException ex) {
                // Another instance created the role at the same time; nothing to do.
                log.debug("Role {} was created concurrently", name);
            }
        });
        log.info("Configured roles synchronised: {}", properties.roles().keySet());
    }
}
