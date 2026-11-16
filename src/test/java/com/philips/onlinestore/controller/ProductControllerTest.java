package com.philips.onlinestore.controller;

import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.dto.ProductResponse;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void setUpSecurityMocks() {
        lenient().when(jwtService.isTokenValid("admin-token")).thenReturn(true);
        lenient().when(jwtService.extractSubject("admin-token")).thenReturn("admin@example.com");
        lenient().when(userDetailsService.loadUserByUsername("admin@example.com"))
                .thenReturn(User.withUsername("admin@example.com")
                        .password("not-used")
                        .roles("ADMIN")
                        .build());
    }

    @Test
    void createProductReturnsCreatedResponseAndLocation() throws Exception {
        when(productService.createProduct(any()))
                .thenReturn(productResponse());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Headphones",
                                  "sku":"PH-100",
                                  "description":"Wireless headphones",
                                  "price":49.99,
                                  "stockQuantity":12,
                                  "active":true,
                                  "categoryId":3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/1"))
                .andExpect(jsonPath("$.sku").value("PH-100"));
    }

    @Test
    void getProductsPassesFiltersAndPaginationToService() throws Exception {
        when(productService.getProducts(
                eq("head"),
                eq(3L),
                eq(true),
                any(Pageable.class)
        )).thenAnswer(invocation -> new PageImpl<>(
                List.of(productResponse()),
                invocation.getArgument(3),
                1
        ));

        mockMvc.perform(get("/api/v1/products")
                        .param("search", "head")
                        .param("categoryId", "3")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Headphones"))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void createProductRejectsInvalidPrice() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Headphones",
                                  "sku":"PH-100",
                                  "price":0,
                                  "stockQuantity":12,
                                  "active":true,
                                  "categoryId":3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private ProductResponse productResponse() {
        return new ProductResponse(
                1L,
                "Headphones",
                "PH-100",
                "Wireless headphones",
                new BigDecimal("49.99"),
                12,
                true,
                3L,
                "Audio",
                null
        );
    }
}
