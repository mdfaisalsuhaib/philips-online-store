package com.philips.onlinestore.bootstrap;

import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminFullName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.full-name:Local Admin}") String adminFullName,
            @Value("${app.bootstrap.admin.email:}") String adminEmail,
            @Value("${app.bootstrap.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminFullName = adminFullName == null || adminFullName.isBlank() ? "Local Admin" : adminFullName;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Local admin bootstrap requires APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD."
            );
        }

        User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingUser != null) {
            if (existingUser.getRole() == UserRole.ADMIN) {
                existingUser.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existingUser);
                log.info("Local admin password was updated for email: {}", normalizedEmail);
                return;
            }
            throw new IllegalStateException(
                    "Email already in use by a non-admin account: " + normalizedEmail
            );
        }

        User admin = new User(
                adminFullName,
                normalizedEmail,
                passwordEncoder.encode(adminPassword),
                UserRole.ADMIN
        );
        admin.setEnabled(true);

        userRepository.save(admin);
        log.info("Created local admin user with email: {}", normalizedEmail);
    }
}
