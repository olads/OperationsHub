package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.RefreshToken;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final InvitationService invitationService;

    @Transactional
    public RegisterResponse acceptInvitationAndRegister(AcceptInvitationRequest request, UUID currentOrgId) {
        Invitation invitation = invitationService.getInvitationByToken(request.getToken());

        if (!invitation.getOrganization().getId().equals(currentOrgId)) {
            throw new ConflictException("Invitation is not for the current organization");
        }

        if (invitation.getAccepted()) {
            throw new ConflictException("Invitation has already been accepted");
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Invitation has expired");
        }

        if (userRepository.existsByEmailAndOrganization_Id(invitation.getEmail(), currentOrgId)) {
            throw new ConflictException("User already exists in this organization");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(invitation.getEmail())
                .passwordHash(passwordHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(invitation.getRole())
                .organization(invitation.getOrganization())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build();
        user = userRepository.save(user);

        invitationService.markAsAccepted(invitation);

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .organizationId(invitation.getOrganization().getId())
                .organizationSlug(invitation.getOrganization().getSlug())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request, UUID currentOrgId) {
        User user = userRepository.findByEmailAndOrganization_Id(request.getEmail(), currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password for this organization"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password for this organization");
        }

        Organization org = user.getOrganization();

        String accessToken = jwtService.generateAccessToken(user, org.getId(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, org);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request, UUID currentOrgId) {
        RefreshToken validToken = refreshTokenService.validate(request.getRefreshToken(), currentOrgId);
        
        User user = validToken.getUser();
        Organization org = validToken.getOrganization();

        String accessToken = jwtService.generateAccessToken(user, org.getId(), user.getRole());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, org);

        refreshTokenService.revoke(validToken.getToken());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }
}
