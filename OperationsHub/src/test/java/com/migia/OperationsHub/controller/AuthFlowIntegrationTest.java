package com.migia.OperationsHub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.dto.auth.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("opshub_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupUser() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFirstName("John");
        registerReq.setLastName("Doe");
        registerReq.setEmail("john.doe@test.com");
        registerReq.setPassword("Password123!");
        registerReq.setOrganizationName("Test Org");
        registerReq.setOrganizationSlug("test-org");

        mockMvc.perform(post("/test-org/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)));
    }

    @Test
    void authFlow_success() throws Exception {
        // 1. Login
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("john.doe@test.com");
        loginReq.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/test-org/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String accessToken = loginResponse.getAccessToken();
        String refreshToken = loginResponse.getRefreshToken();

        // 2. Access protected endpoint
        mockMvc.perform(get("/test-org/api/v1/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@test.com"));

        // 3. Refresh token
        RefreshRequest refreshReq = new RefreshRequest();
        refreshReq.setRefreshToken(refreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/test-org/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        LoginResponse refreshedResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), LoginResponse.class);
        String newRefreshToken = refreshedResponse.getRefreshToken();

        // 4. Logout
        LogoutRequest logoutReq = new LogoutRequest();
        logoutReq.setRefreshToken(newRefreshToken);

        mockMvc.perform(post("/test-org/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isNoContent());

        // 5. Attempt refresh again (should fail)
        RefreshRequest failRefreshReq = new RefreshRequest();
        failRefreshReq.setRefreshToken(newRefreshToken);

        mockMvc.perform(post("/test-org/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRefreshReq)))
                .andExpect(status().isConflict()); // Or isUnauthorized depending on exception thrown
    }

    @Test
    void protectedEndpoint_noToken_returns403() throws Exception {
        mockMvc.perform(get("/test-org/api/v1/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_wrongPassword_returns404() throws Exception {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("john.doe@test.com");
        loginReq.setPassword("WrongPassword123!");

        mockMvc.perform(post("/test-org/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isNotFound()); // Due to ResourceNotFoundException in AuthService
    }
}
