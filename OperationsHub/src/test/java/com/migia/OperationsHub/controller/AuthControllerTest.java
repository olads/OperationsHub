package com.migia.OperationsHub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migia.OperationsHub.Service.AuthService;
import com.migia.OperationsHub.dto.auth.RegisterRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_validPayload_returns201() throws Exception {
        RegisterResponse mockResponse = RegisterResponse.builder()
                .userId(UUID.randomUUID())
                .email("jane@example.com")
                .organizationId(UUID.randomUUID())
                .organizationSlug("acme-corp")
                .build();

        when(authService.register(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.organizationSlug").value("acme-corp"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new ConflictException("An account with email 'jane@example.com' already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with email 'jane@example.com' already exists"));
    }

    @Test
    void register_invalidEmail_returns400WithViolations() throws Exception {
        String payload = """
                {
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "email": "not-an-email",
                  "password": "Password1!",
                  "organizationName": "Acme",
                  "organizationSlug": "acme"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String payload = """
                {
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "email": "jane@example.com",
                  "password": "short",
                  "organizationName": "Acme",
                  "organizationSlug": "acme"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    private String validPayload() {
        return """
                {
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "email": "jane@example.com",
                  "password": "Password1!",
                  "organizationName": "Acme Corp",
                  "organizationSlug": "acme-corp"
                }
                """;
    }
}
