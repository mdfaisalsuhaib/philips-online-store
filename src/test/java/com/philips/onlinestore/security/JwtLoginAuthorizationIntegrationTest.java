package com.philips.onlinestore.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.repository.OrderRepository;
import com.philips.onlinestore.repository.PaymentRepository;
import com.philips.onlinestore.repository.ProductRepository;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=always",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "jwt.expiration-ms=900000"
})
@AutoConfigureMockMvc
class JwtLoginAuthorizationIntegrationTest {

    private static final String CUSTOMER_EMAIL = "jwt-flow-customer@example.com";
    private static final String CUSTOMER_PASSWORD = "LocalTestPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User(
                "JWT Flow Customer",
                CUSTOMER_EMAIL,
                passwordEncoder.encode(CUSTOMER_PASSWORD),
                UserRole.CUSTOMER
        );
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
    }

    @Test
    void jwtGeneratedByLoginAuthenticatesOrderEndpoint() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"jwt-flow-customer@example.com",
                                  "password":"LocalTestPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginResponse = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginResponse.path("token").asText();
        assertFalse(token.isBlank());
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(CUSTOMER_EMAIL, jwtService.extractSubject(token));
        assertEquals(900_000, loginResponse.path("expiresInMs").asLong());
        JsonNode jwtHeader = objectMapper.readTree(
                Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.')))
        );
        assertEquals("HS256", jwtHeader.path("alg").asText());

        MvcResult ordersResult = mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("[]", ordersResult.getResponse().getContentAsString());
        assertEquals("[]", ordersResult.getResponse().getContentAsString());
    }
}
