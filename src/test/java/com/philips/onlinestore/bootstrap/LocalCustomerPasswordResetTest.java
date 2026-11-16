package com.philips.onlinestore.bootstrap;

import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCustomerPasswordResetTest {

    private static final String RESET_PASSWORD = "LocalTestPassword123";

    @Mock
    private UserRepository userRepository;

    @Test
    void looksUpExistingCustomerHashesNewPasswordAndPreservesAccountFields() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        User existingUser = new User(
                "Existing Customer",
                "faisal.test@gmail.com",
                passwordEncoder.encode("PreviousTestPassword123"),
                UserRole.CUSTOMER
        );
        existingUser.setEnabled(false);
        when(userRepository.findByEmail("faisal.test@gmail.com")).thenReturn(Optional.of(existingUser));

        LocalCustomerPasswordReset reset = new LocalCustomerPasswordReset(
                userRepository,
                passwordEncoder,
                RESET_PASSWORD
        );
        reset.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).findByEmail("faisal.test@gmail.com");
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("Existing Customer", savedUser.getFullName());
        assertEquals("faisal.test@gmail.com", savedUser.getEmail());
        assertEquals(UserRole.CUSTOMER, savedUser.getRole());
        assertFalse(savedUser.isEnabled());
        assertTrue(passwordEncoder.matches(RESET_PASSWORD, savedUser.getPassword()));
        assertFalse(RESET_PASSWORD.equals(savedUser.getPassword()));
    }

    @Test
    void doesNotSaveWhenTheCustomerAccountDoesNotExist() {
        when(userRepository.findByEmail("faisal.test@gmail.com")).thenReturn(Optional.empty());
        LocalCustomerPasswordReset reset = new LocalCustomerPasswordReset(
                userRepository,
                new BCryptPasswordEncoder(),
                RESET_PASSWORD
        );

        assertThrows(IllegalStateException.class, () -> reset.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void refusesToChangeAnAccountThatIsNotACustomer() {
        User admin = new User(
                "Existing Admin",
                "faisal.test@gmail.com",
                "existing-hash",
                UserRole.ADMIN
        );
        when(userRepository.findByEmail("faisal.test@gmail.com")).thenReturn(Optional.of(admin));
        LocalCustomerPasswordReset reset = new LocalCustomerPasswordReset(
                userRepository,
                new BCryptPasswordEncoder(),
                RESET_PASSWORD
        );

        assertThrows(IllegalStateException.class, () -> reset.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        assertEquals("existing-hash", admin.getPassword());
    }

    @Test
    void rejectsMissingOrInvalidPasswordWithoutSaving() {
        LocalCustomerPasswordReset reset = new LocalCustomerPasswordReset(
                userRepository,
                new BCryptPasswordEncoder(),
                ""
        );

        assertThrows(IllegalStateException.class, () -> reset.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void resetIsDisabledByDefaultAndRequiresTheLocalProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalCustomerPasswordReset.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new)
                .run(context -> assertFalse(context.containsBean("localCustomerPasswordReset")));

        new ApplicationContextRunner()
                .withUserConfiguration(LocalCustomerPasswordReset.class)
                .withPropertyValues(
                        "app.customer-password-reset.enabled=true",
                        "app.customer-password-reset.password=" + RESET_PASSWORD
                )
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new)
                .run(context -> assertFalse(context.containsBean("localCustomerPasswordReset")));
    }

    @Test
    void resetBeanIsCreatedOnlyWithExplicitEnablementAndLocalProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalCustomerPasswordReset.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(
                        "app.customer-password-reset.enabled=true",
                        "app.customer-password-reset.password=" + RESET_PASSWORD
                )
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new)
                .run(context -> assertTrue(context.containsBean("localCustomerPasswordReset")));
    }
}
