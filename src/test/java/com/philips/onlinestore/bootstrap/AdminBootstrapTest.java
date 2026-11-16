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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void createsAdminWhenEmailIsAvailable() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                "Local Admin",
                "  ADMIN@EXAMPLE.COM ",
                "StrongAdminPassword123"
        );
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("admin@example.com", savedUser.getEmail());
        assertEquals(UserRole.ADMIN, savedUser.getRole());
        assertTrue(savedUser.isEnabled());
        assertTrue(passwordEncoder.matches("StrongAdminPassword123", savedUser.getPassword()));
    }

    @Test
    void updatesPasswordWhenAdminAlreadyExists() {
        PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder());
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                "Local Admin",
                "admin@example.com",
                "StrongAdminPassword123"
        );
        User existingAdmin = new User(
                "Local Admin",
                "admin@example.com",
                passwordEncoder.encode("ExistingPassword123"),
                UserRole.ADMIN
        );
        existingAdmin.setEnabled(true);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals(existingAdmin, savedUser);
        assertEquals(UserRole.ADMIN, savedUser.getRole());
        assertTrue(savedUser.isEnabled());
        assertTrue(passwordEncoder.matches("StrongAdminPassword123", savedUser.getPassword()));
        verify(passwordEncoder).encode("StrongAdminPassword123");
    }

    @Test
    void rejectsExistingNonAdminUserWithTheSameEmail() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                "Local Admin",
                "customer@example.com",
                "StrongAdminPassword123"
        );
        User existingCustomer = new User(
                "Customer",
                "customer@example.com",
                passwordEncoder.encode("CustomerPassword123"),
                UserRole.CUSTOMER
        );
        existingCustomer.setEnabled(true);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(existingCustomer));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments())
        );

        assertTrue(exception.getMessage().contains("non-admin"));
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }
}
