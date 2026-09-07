package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.MembershipRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.Service.AuthService;
import com.migia.OperationsHub.dto.auth.RegisterRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
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
    @Mock private MembershipRepository membershipRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = buildRequest("jane@example.com", "acme-corp");
    }

    @Test
    void register_success_createsUserOrgAndMembership() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");

        User savedUser = User.builder()
                .email("jane@example.com").passwordHash("$2a$hashed")
                .firstName("Jane").lastName("Doe").status(UserStatus.ACTIVE).build();
        savedUser = spy(savedUser);
        doReturn(UUID.randomUUID()).when(savedUser).getId();

        Organization savedOrg = Organization.builder()
                .name("Acme Corp").slug("acme-corp").status(OrganizationStatus.ACTIVE).build();
        savedOrg = spy(savedOrg);
        doReturn(UUID.randomUUID()).when(savedOrg).getId();

        when(userRepository.save(any())).thenReturn(savedUser);
        when(organizationRepository.save(any())).thenReturn(savedOrg);

        RegisterResponse response = authService.register(validRequest);

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getOrganizationSlug()).isEqualTo("acme-corp");

        // Verify password was hashed
        verify(passwordEncoder).encode("Password1!");

        // Verify membership was created with ORGANIZATION_OWNER role
        ArgumentCaptor<com.migia.OperationsHub.model.Membership> membershipCaptor =
                ArgumentCaptor.forClass(com.migia.OperationsHub.model.Membership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(Role.ORGANIZATION_OWNER);
    }

    @Test
    void register_duplicateEmail_throwsConflictException() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jane@example.com");

        verifyNoInteractions(organizationRepository, membershipRepository, passwordEncoder);
    }

    @Test
    void register_duplicateSlug_throwsConflictException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("acme-corp");

        verifyNoInteractions(membershipRepository, passwordEncoder);
    }

    @Test
    void register_passwordIsNeverPlaintext() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        User savedUser = mock(User.class);
        when(savedUser.getId()).thenReturn(UUID.randomUUID());
        when(savedUser.getEmail()).thenReturn("jane@example.com");
        Organization savedOrg = mock(Organization.class);
        when(savedOrg.getId()).thenReturn(UUID.randomUUID());
        when(savedOrg.getSlug()).thenReturn("acme-corp");

        when(userRepository.save(any())).thenReturn(savedUser);
        when(organizationRepository.save(any())).thenReturn(savedOrg);

        authService.register(validRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        // Verify saved user has hashed password, not plaintext
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(userCaptor.getValue().getPasswordHash()).doesNotContain("Password1!");
    }

    private RegisterRequest buildRequest(String email, String slug) {
        RegisterRequest r = new RegisterRequest();
        // Use reflection or make fields package-private for test; here we use a helper builder approach
        // In real project consider using @Value or TestRegisterRequest builder
        try {
            setField(r, "email", email);
            setField(r, "password", "Password1!");
            setField(r, "firstName", "Jane");
            setField(r, "lastName", "Doe");
            setField(r, "organizationName", "Acme Corp");
            setField(r, "organizationSlug", slug);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    private void setField(Object target, String name, String value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
