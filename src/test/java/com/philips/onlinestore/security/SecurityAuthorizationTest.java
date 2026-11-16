package com.philips.onlinestore.security;

import com.philips.onlinestore.config.SecurityConfig;
import com.philips.onlinestore.controller.AuthController;
import com.philips.onlinestore.controller.CartController;
import com.philips.onlinestore.controller.CategoryController;
import com.philips.onlinestore.controller.HealthController;
import com.philips.onlinestore.controller.OrderController;
import com.philips.onlinestore.controller.PaymentController;
import com.philips.onlinestore.controller.ProductController;
import com.philips.onlinestore.dto.CategoryResponse;
import com.philips.onlinestore.dto.LoginResponse;
import com.philips.onlinestore.dto.OrderResponse;
import com.philips.onlinestore.dto.ProductResponse;
import com.philips.onlinestore.dto.UserResponse;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.CategoryService;
import com.philips.onlinestore.service.CartService;
import com.philips.onlinestore.service.OrderService;
import com.philips.onlinestore.service.PaymentService;
import com.philips.onlinestore.service.ProductService;
import com.philips.onlinestore.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        CartController.class,
        CategoryController.class,
        HealthController.class,
        OrderController.class,
        PaymentController.class,
        ProductController.class
})
@Import(SecurityConfig.class)
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        stubToken("customer-token", "customer@example.com", "CUSTOMER");
        stubToken("admin-token", "admin@example.com", "ADMIN");

        when(productService.createProduct(any())).thenReturn(productResponse());
        when(productService.updateProduct(eq(1L), any())).thenReturn(productResponse());
        when(categoryService.createCategory(any())).thenReturn(categoryResponse());
        when(categoryService.updateCategory(eq(1L), any())).thenReturn(categoryResponse());
        when(productService.getProducts(any(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(3)));
        when(categoryService.getAllCategories()).thenReturn(List.of());
        when(userService.register(any())).thenReturn(userResponse());
        when(userService.login(any())).thenReturn(loginResponse());
    }

    @Test
    void publicEndpointsRemainAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"New Customer",
                                  "email":"new@example.com",
                                  "password":"StrongPassword123",
                                  "role":"ADMIN"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"StrongPassword123"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrInvalidBearerTokenReturnsUnauthorizedForWriteEndpoints() throws Exception {
        mockMvc.perform(productCreateRequest())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders/4/payments")
                        .header("Idempotency-Key", "authorization-test"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":1}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(categoryCreateRequest())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(productCreateRequest().header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void errorDispatcherIsPermittedWithoutMakingPaymentEndpointsPublic() throws Exception {
        mockMvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            request.setAttribute(
                                    RequestDispatcher.ERROR_EXCEPTION,
                                    new IllegalStateException("diagnostic error")
                            );
                            return request;
                        }))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));

        mockMvc.perform(post("/api/v1/orders/4/payments")
                        .header("Idempotency-Key", "authorization-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateUpdateOrDeleteProductsOrCategories() throws Exception {
        mockMvc.perform(productCreateRequest().header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/1")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/products/1")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(categoryCreateRequest().header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/categories/1")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categories/1")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUpdateAndDeleteProductsAndCategories() throws Exception {
        mockMvc.perform(productCreateRequest().header("Authorization", "Bearer admin-token"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/products/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/products/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        mockMvc.perform(categoryCreateRequest().header("Authorization", "Bearer admin-token"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/categories/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/categories/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
    }

    private void stubToken(String token, String email, String role) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractSubject(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email))
                .thenReturn(User.withUsername(email)
                        .password("not-used")
                        .roles(role)
                        .build());
    }

    private MockHttpServletRequestBuilder productCreateRequest() {
        return post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson());
    }

    private MockHttpServletRequestBuilder categoryCreateRequest() {
        return post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson());
    }

    private String productJson() {
        return """
                {
                  "name":"Test Product",
                  "sku":"TEST-001",
                  "description":"Test",
                  "price":10.00,
                  "stockQuantity":5,
                  "active":true,
                  "categoryId":1
                }
                """;
    }

    private String categoryJson() {
        return """
                {"name":"Test Category","description":"Test"}
                """;
    }

    private ProductResponse productResponse() {
        return new ProductResponse(
                1L, "Test Product", "TEST-001", "Test",
                new BigDecimal("10.00"), 5, true, 1L, "Test Category", null
        );
    }

    private CategoryResponse categoryResponse() {
        return new CategoryResponse(1L, "Test Category", "Test");
    }

    private UserResponse userResponse() {
        return new UserResponse(
                1L, "New Customer", "new@example.com", UserRole.CUSTOMER,
                true, Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private LoginResponse loginResponse() {
        return new LoginResponse(
                "test-token", "Bearer", 900_000,
                1L, "New Customer", "new@example.com", UserRole.CUSTOMER,
                true, Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
