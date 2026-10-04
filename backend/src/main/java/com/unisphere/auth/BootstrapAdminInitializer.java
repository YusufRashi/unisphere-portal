package com.unisphere.auth;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import com.unisphere.user.UserRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from {@code BOOTSTRAP_ADMIN_EMAIL} / {@code BOOTSTRAP_ADMIN_PASSWORD}
 * when no administrator exists yet. This avoids shipping default credentials in code or migrations.
 * After first start-up the variables can (and should) be removed.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final UniSphereProperties.BootstrapAdmin settings;
    private final Clock clock;

    public BootstrapAdminInitializer(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit,
                                     UniSphereProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.settings = properties.bootstrapAdmin();
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (!settings.isConfigured()) {
            log.warn("No administrator exists. Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD to create one.");
            return;
        }
        if (users.existsByEmail(settings.email())) {
            log.error("Cannot bootstrap admin: a non-admin user with email {} already exists.", settings.email());
            return;
        }
        PasswordPolicy.validate(settings.password(), settings.email());

        User admin = new User(settings.email(), settings.firstName(), settings.lastName(), Set.of(Role.ADMIN), clock.instant());
        admin.activate(passwordEncoder.encode(settings.password()));
        users.save(admin);
        audit.recordAs(null, AuditAction.USER_CREATED, "User", admin.getId(), Map.of("source", "bootstrap"));
        log.info("Bootstrap administrator {} created.", admin.getEmail());
    }
}
