package com.org.ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.entity.Role;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.repository.RoleRepository;
import com.org.ecommerce.repository.UserRepository;
import com.org.ecommerce.security.TokenHasher;
import com.org.ecommerce.validation.StrongPasswordValidator;

/**
 * Creates the first administrator from APP_BOOTSTRAP_ADMIN_EMAIL / APP_BOOTSTRAP_ADMIN_PASSWORD
 * when no administrator exists yet. Credentials never live in migrations or source control.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(AppProperties properties, UserRepository users, RoleRepository roles,
                                     PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || admin.email() == null || admin.email().isBlank()
                || admin.password() == null || admin.password().isBlank()) {
            return;
        }
        if (users.countByRole(Role.ADMIN) > 0) {
            return;
        }
        if (!new StrongPasswordValidator().isValid(admin.password(), null)) {
            log.error("Bootstrap admin not created: APP_BOOTSTRAP_ADMIN_PASSWORD does not meet the password policy");
            return;
        }
        String email = TokenHasher.normalizeEmail(admin.email());
        User user = users.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        if (user.getFirstName() == null) {
            user.setFirstName("Store");
            user.setLastName("Admin");
        }
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.getRoles().add(roles.findByName(Role.ADMIN).orElseThrow());
        users.save(user);
        log.info("Bootstrap administrator account created");
    }
}
