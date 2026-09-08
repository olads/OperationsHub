package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.MembershipRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.dto.auth.RegisterRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Membership;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.RefreshToken;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Validate uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with email '" + request.getEmail() + "' already exists");
        }
        if (organizationRepository.existsBySlug(request.getOrganizationSlug())) {
            throw new ConflictException("An organization with slug '" + request.getOrganizationSlug() + "' already exists");
        }

        // 2. Hash password — never store plaintext
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 3. Persist User
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        // 4. Persist Organization
        Organization organization = Organization.builder()
                .name(request.getOrganizationName())
                .slug(request.getOrganizationSlug())
                .status(OrganizationStatus.ACTIVE)
                .build();
        organization = organizationRepository.save(organization);

        // 5. Persist Membership (owner role)
        Membership membership = Membership.builder()
                .user(user)
                .organization(organization)
                .role(Role.ORGANIZATION_OWNER)
                .build();
        membershipRepository.save(membership);

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .organizationId(organization.getId())
                .organizationSlug(organization.getSlug())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request, UUID currentOrgId) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        Membership membership = membershipRepository.findByUser_EmailAndOrganization_Id(user.getEmail(), currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found in this organization"));

        Organization org = membership.getOrganization();

        String accessToken = jwtService.generateAccessToken(user, org.getId(), membership.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, org);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request, UUID currentOrgId) {
        RefreshToken validToken = refreshTokenService.validate(request.getRefreshToken(), currentOrgId);
        
        User user = validToken.getUser();
        Organization org = validToken.getOrganization();

        Membership membership = membershipRepository.findByUser_EmailAndOrganization_Id(user.getEmail(), org.getId())
                .orElseThrow(() -> new ConflictException("Membership revoked"));

        String accessToken = jwtService.generateAccessToken(user, org.getId(), membership.getRole());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, org);

        refreshTokenService.revoke(validToken.getToken());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .build();
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }
}
