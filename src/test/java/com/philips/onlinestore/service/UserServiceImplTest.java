package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.RegisterRequest;
import com.philips.onlinestore.dto.LoginRequest;
import com.philips.onlinestore.dto.LoginResponse;
import com.philips.onlinestore.dto.UserResponse;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.EmailAlreadyExistsException;
import com.philips.onlinestore.exception.InvalidCredentialsException;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(
                "dGVzdC1zaWduaW5nLWtleS13aXRoLWF0LWxlYXN0LTMyLWJ5dGVz",
                60_000
        );
        userService = new UserServiceImpl(userRepository, passwordEncoder, jwtService, 60_000);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndAlwaysAssignsCustomerRole() {
        RegisterRequest request = new RegisterRequest(
                "Test Customer",
                "  CUSTOMER@EXAMPLE.COM ",
                "StrongPassword123"
        );
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("customer@example.com", savedUser.getEmail());
        assertTrue(passwordEncoder.matches("StrongPassword123", savedUser.getPassword()));
        assertFalse(savedUser.getPassword().equals("StrongPassword123"));
        assertEquals(UserRole.CUSTOMER, savedUser.getRole());
        assertTrue(savedUser.isEnabled());
        assertEquals("customer@example.com", response.email());
        assertEquals(UserRole.CUSTOMER, response.role());
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "Test Customer",
                "customer@example.com",
                "StrongPassword123"
        );
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(new User(
                        "Existing Customer",
                        "customer@example.com",
                        "existing-hash",
                        UserRole.CUSTOMER
                )));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.register(request));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void loginNormalizesEmailAndReturnsSafeUserDetails() {
        String encodedPassword = passwordEncoder.encode("StrongPassword123");
        User user = new User("Test Customer", "customer@example.com", encodedPassword, UserRole.CUSTOMER);
        user.setEnabled(true);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = userService.login(
                new LoginRequest(" CUSTOMER@EXAMPLE.COM ", "StrongPassword123")
        );

        assertEquals("customer@example.com", response.email());
        assertEquals(UserRole.CUSTOMER, response.role());
        assertTrue(response.enabled());
        assertEquals("Bearer", response.tokenType());
        assertEquals(60_000, response.expiresInMs());
        assertEquals("customer@example.com", jwtService.extractSubject(response.token()));
        assertTrue(jwtService.isTokenValid(response.token()));
        assertFalse(response.toString().contains("password"));
        assertFalse(response.toString().contains("hash"));
    }

    @Test
    void loginRejectsIncorrectPassword() {
        User user = new User(
                "Test Customer",
                "customer@example.com",
                passwordEncoder.encode("CorrectPassword123"),
                UserRole.CUSTOMER
        );
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("customer@example.com", "WrongPassword123"))
        );
    }

    @Test
    void loginRejectsUnknownEmailWithGenericCredentialsError() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("unknown@example.com", "SomePassword123"))
        );

        assertEquals("Invalid email or password.", exception.getMessage());
    }

    @Test
    void loginRejectsDisabledUser() {
        User user = new User(
                "Disabled Customer",
                "disabled@example.com",
                passwordEncoder.encode("StrongPassword123"),
                UserRole.CUSTOMER
        );
        user.setEnabled(false);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login(new LoginRequest("disabled@example.com", "StrongPassword123"))
        );

        assertEquals("Invalid email or password.", exception.getMessage());
    }
}
