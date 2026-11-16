package com.philips.onlinestore.controller;

import com.philips.onlinestore.dto.CategoryResponse;
import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpSecurityMocks() {
        when(jwtService.isTokenValid("admin-token")).thenReturn(true);
        when(jwtService.extractSubject("admin-token")).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com"))
                .thenReturn(User.withUsername("admin@example.com")
                        .password("not-used")
                        .roles("ADMIN")
                        .build());
    }

    @Test
    void createCategoryReturnsCreatedResponseAndLocation() throws Exception {
        when(categoryService.createCategory(any()))
                .thenReturn(new CategoryResponse(1L, "Audio", "Audio products"));

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Audio","description":"Audio products"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/categories/1"))
                .andExpect(jsonPath("$.name").value("Audio"));
    }

    @Test
    void createCategoryRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","description":"Audio products"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }
}
