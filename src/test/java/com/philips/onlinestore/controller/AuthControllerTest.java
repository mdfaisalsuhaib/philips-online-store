package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.UserResponse;
import com.philips.onlinestore.dto.LoginResponse;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.EmailAlreadyExistsException;
import com.philips.onlinestore.exception.InvalidCredentialsException;
import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void registerReturnsCreatedUserWithoutPasswordFields() throws Exception {
        when(userService.register(any()))
                .thenReturn(new UserResponse(
                        1L,
                        "Test Customer",
                        "customer@example.com",
                        UserRole.CUSTOMER,
                        true,
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Test Customer",
                                  "email":"customer@example.com",
                                  "password":"StrongPassword123",
                                  "role":"ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":" ",
                                  "email":"not-an-email",
                                  "password":"short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyExists() throws Exception {
        when(userService.register(any()))
                .thenThrow(new EmailAlreadyExistsException("customer@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Test Customer",
                                  "email":"customer@example.com",
                                  "password":"StrongPassword123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturnsSafeUserDetails() throws Exception {
        when(userService.login(any()))
                .thenReturn(new LoginResponse(
                        "signed.jwt.token",
                        "Bearer",
                        900_000,
                        1L,
                        "Test Customer",
                        "customer@example.com",
                        UserRole.CUSTOMER,
                        true,
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"customer@example.com",
                                  "password":"StrongPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMs").value(900000))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginRejectsInvalidRequestFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"not-an-email",
                                  "password":" "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void loginReturnsUnauthorizedForIncorrectPassword() throws Exception {
        when(userService.login(any())).thenThrow(new InvalidCredentialsException());

        performInvalidLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void loginReturnsSameUnauthorizedResponseForUnknownEmail() throws Exception {
        when(userService.login(any())).thenThrow(new InvalidCredentialsException());

        performInvalidLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void loginReturnsSameUnauthorizedResponseForDisabledUser() throws Exception {
        when(userService.login(any())).thenThrow(new InvalidCredentialsException());

        performInvalidLogin()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    private org.springframework.test.web.servlet.ResultActions performInvalidLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"customer@example.com",
                          "password":"WrongPassword123"
                        }
                        """));
    }
}
