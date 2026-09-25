package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.InvitationRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.Service.AuthService;
import com.migia.OperationsHub.Service.InvitationService;
import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.Service.RefreshTokenService;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private InvitationService invitationService;

    @InjectMocks
    private AuthService authService;

    private Organization organization;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        organization = Organization.builder()
                .id(orgId)
                .name("Acme Corp")
                .slug("acme-corp")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    /**
     * Test Case: Accept Invitation - Success
     * Verifies that when a valid, unexpired invitation token is provided:
     * - A new User is saved with the hashed password
     * - The invitation is marked as accepted
     * - The response contains correct email and org details
     */
    @Test
    void acceptInvitationAndRegister_success() {
        Invitation invitation = Invitation.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .organization(organization)
                .role(Role.EMPLOYEE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .accepted(false)
                .build();

        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("valid-token");
        request.setPassword("Password1!");
        request.setFirstName("Jane");
        request.setLastName("Doe");

        when(invitationService.getInvitationByToken("valid-token")).thenReturn(invitation);
        when(userRepository.existsByEmailAndOrganization_Id("jane@example.com", orgId)).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("$2a$hashed")
                .firstName("Jane")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .organization(organization)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.acceptInvitationAndRegister(request, orgId);

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getOrganizationSlug()).isEqualTo("acme-corp");
        verify(passwordEncoder).encode("Password1!");
        verify(invitationService).markAsAccepted(invitation);

        // Verify saved user always has a hashed password, never plaintext
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
    }

    /**
     * Test Case: Accept Invitation - Already Accepted
     * Verifies that attempting to use an already-accepted invitation token
     * throws a ConflictException, preventing duplicate registrations.
     */
    @Test
    void acceptInvitationAndRegister_alreadyAccepted_throwsConflict() {
        Invitation invitation = Invitation.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .organization(organization)
                .role(Role.EMPLOYEE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .accepted(true) // Already accepted
                .build();

        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("used-token");
        request.setPassword("Password1!");
        request.setFirstName("Jane");
        request.setLastName("Doe");

        when(invitationService.getInvitationByToken("used-token")).thenReturn(invitation);

        assertThatThrownBy(() -> authService.acceptInvitationAndRegister(request, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been accepted");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    /**
     * Test Case: Accept Invitation - Expired Token
     * Verifies that an expired invitation token is rejected with a ConflictException.
     */
    @Test
    void acceptInvitationAndRegister_expiredToken_throwsConflict() {
        Invitation invitation = Invitation.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .organization(organization)
                .role(Role.EMPLOYEE)
                .expiresAt(Instant.now().minusSeconds(3600)) // Already expired
                .accepted(false)
                .build();

        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("expired-token");
        request.setPassword("Password1!");
        request.setFirstName("Jane");
        request.setLastName("Doe");

        when(invitationService.getInvitationByToken("expired-token")).thenReturn(invitation);

        assertThatThrownBy(() -> authService.acceptInvitationAndRegister(request, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expired");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    /**
     * Test Case: Login - Success
     * Verifies that valid credentials produce a login response containing
     * an access token and refresh token.
     */
    @Test
    void login_success_returnsTokens() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("$2a$hashed")
                .role(Role.EMPLOYEE)
                .organization(organization)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build();

        com.migia.OperationsHub.model.RefreshToken refreshToken =
                new com.migia.OperationsHub.model.RefreshToken();
        refreshToken.setToken("refresh-token-xyz");
        refreshToken.setOrganization(organization);
        refreshToken.setUser(user);

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("Password1!");

        when(userRepository.findByEmailAndOrganization_Id("jane@example.com", orgId))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "$2a$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user, orgId, Role.EMPLOYEE)).thenReturn("access-token-abc");
        when(refreshTokenService.createRefreshToken(user, organization)).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.login(request, orgId);

        assertThat(response.getAccessToken()).isEqualTo("access-token-abc");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(response.isMustChangePassword()).isFalse();
    }

    /**
     * Test Case: Login - Wrong Password
     * Verifies that providing incorrect credentials throws a ResourceNotFoundException.
     * The error message must NOT reveal whether it's the email or password that is wrong,
     * to prevent user enumeration attacks.
     */
    @Test
    void login_wrongPassword_throwsResourceNotFoundException() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("$2a$hashed")
                .role(Role.EMPLOYEE)
                .organization(organization)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("WrongPassword!");

        when(userRepository.findByEmailAndOrganization_Id("jane@example.com", orgId))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, orgId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(jwtService, never()).generateAccessToken(any(), any(), any());
    }

    /**
     * Test Case: Login - User Not Found
     * Verifies that attempting to log in with a non-existent email (within the org)
     * throws a ResourceNotFoundException without leaking user enumeration info.
     */
    @Test
    void login_userNotFound_throwsResourceNotFoundException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("Password1!");

        when(userRepository.findByEmailAndOrganization_Id("ghost@example.com", orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, orgId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(passwordEncoder, jwtService);
    }
}
