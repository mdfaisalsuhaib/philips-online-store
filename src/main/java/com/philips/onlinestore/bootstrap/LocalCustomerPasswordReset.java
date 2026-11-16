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

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.customer-password-reset", name = "enabled", havingValue = "true")
public class LocalCustomerPasswordReset implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCustomerPasswordReset.class);
    private static final String CUSTOMER_EMAIL = "faisal.test@gmail.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String newPassword;

    public LocalCustomerPasswordReset(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.customer-password-reset.password:}") String newPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.newPassword = newPassword == null ? "" : newPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (newPassword.isBlank() || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new IllegalStateException(
                    "Local customer password reset requires APP_CUSTOMER_PASSWORD_RESET_PASSWORD "
                            + "with 8 to 72 characters."
            );
        }

        User user = userRepository.findByEmail(CUSTOMER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "The configured local customer account was not found."
                ));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new IllegalStateException(
                    "The configured local account is not a customer; password reset was not performed."
            );
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Local customer password reset completed.");
    }
}
