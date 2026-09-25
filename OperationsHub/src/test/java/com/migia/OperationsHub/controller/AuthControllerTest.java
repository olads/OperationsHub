package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Service.AuthService;
import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.GlobalExceptionHandler;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link com.migia.OperationsHub.RestControllers.AuthController}.
 * Security filters are disabled ({@code addFilters = false}) so these tests
 * focus purely on request/response serialization and controller-layer logic.
 */
@WebMvcTest(com.migia.OperationsHub.RestControllers.AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JacksonAutoConfiguration.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private JwtService jwtService;

    /**
     * Test Case: Accept Invitation - Valid Payload
     * Verifies that a well-formed invitation acceptance request is routed correctly
     * and returns 201 Created with the newly registered user's details.
     */
    @Test
    void acceptInvitation_validPayload_returns201() throws Exception {
        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("valid-token");
        request.setPassword("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        RegisterResponse mockResponse = RegisterResponse.builder()
                .userId(UUID.randomUUID())
                .email("john@example.com")
                .organizationId(UUID.randomUUID())
                .organizationSlug("acme-corp")
                .build();

        when(authService.acceptInvitationAndRegister(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/acme-corp/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.organizationSlug").value("acme-corp"));
    }

    /**
     * Test Case: Accept Invitation - Missing Required Fields
     * Verifies that a request without required fields (token, password, firstName, lastName)
     * is rejected with a 400 Bad Request before reaching the service layer.
     */
    @Test
    void acceptInvitation_missingFields_returns400() throws Exception {
        String payload = "{}"; // Empty body, all required fields are missing

        mockMvc.perform(post("/acme-corp/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Test Case: Accept Invitation - Token Already Used
     * Verifies that a service-layer ConflictException (e.g., expired or used token)
     * is translated into a 409 Conflict HTTP response by the GlobalExceptionHandler.
     */
    @Test
    void acceptInvitation_conflictFromService_returns409() throws Exception {
        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("used-token");
        request.setPassword("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(authService.acceptInvitationAndRegister(any(), any()))
                .thenThrow(new ConflictException("Invitation has already been accepted"));

        mockMvc.perform(post("/acme-corp/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Invitation has already been accepted"));
    }

    /**
     * Test Case: Login - Valid Credentials
     * Verifies that valid login credentials return a 200 OK with
     * access and refresh tokens in the response body.
     */
    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("Password123!");

        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("access-token-abc")
                .refreshToken("refresh-token-xyz")
                .expiresIn(900L)
                .mustChangePassword(false)
                .build();

        when(authService.login(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/acme-corp/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-abc"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-xyz"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    /**
     * Test Case: Login - Invalid Credentials
     * Verifies that when the service throws ResourceNotFoundException (wrong email/password),
     * the controller correctly returns a 404 Not Found response.
     */
    @Test
    void login_invalidCredentials_returns404() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("wrong@example.com");
        request.setPassword("WrongPass!");

        when(authService.login(any(), any()))
                .thenThrow(new ResourceNotFoundException("Invalid email or password for this organization"));

        mockMvc.perform(post("/acme-corp/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * Test Case: Refresh Token - Valid Request
     * Verifies that a valid refresh token request returns a 200 OK
     * with a newly issued access token and a rotated refresh token.
     */
    @Test
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh-token");

        LoginResponse mockResponse = LoginResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(900L)
                .build();

        when(authService.refresh(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/acme-corp/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    /**
     * Test Case: Refresh Token - Missing Refresh Token Field
     * Verifies that a refresh request body without the refreshToken field
     * fails validation and returns a 400 Bad Request.
     */
    @Test
    void refresh_missingToken_returns400() throws Exception {
        String payload = "{}";

        mockMvc.perform(post("/acme-corp/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Case: Logout - Successful
     * Verifies that a logout request with a valid refresh token results in
     * a 204 No Content response and the service's logout method is called.
     */
    @Test
    void logout_validRequest_returns204() throws Exception {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token-to-revoke");

        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/acme-corp/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }
}
