package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.CustomerAccountRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.dto.auth.CustomerRegisterRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.CustomerAccount;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import com.migia.OperationsHub.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final CustomerAccountRepository customerAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(CustomerRegisterRequest request, UUID currentOrgId) {
        Organization org = organizationRepository.findById(currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        if (customerAccountRepository.existsByEmailAndOrganization_Id(request.getEmail(), currentOrgId)) {
            throw new ConflictException("Customer account with this email already exists in this organization");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        CustomerAccount account = CustomerAccount.builder()
                .organization(org)
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .status(CustomerStatus.ACTIVE)
                .build();
                
        account = customerAccountRepository.save(account);

        return RegisterResponse.builder()
                .userId(account.getId())
                .email(account.getEmail())
                .organizationId(org.getId())
                .organizationSlug(org.getSlug())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request, UUID currentOrgId) {
        CustomerAccount account = customerAccountRepository.findByEmailAndOrganization_Id(request.getEmail(), currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        if (account.getStatus() != CustomerStatus.ACTIVE) {
            throw new ConflictException("Account is not active");
        }

        Organization org = account.getOrganization();

        // Use a dummy user object for generating token, or adapt jwtService to take email directly.
        // Let's create a temporary User object just for token generation since JwtService expects a User.
        com.migia.OperationsHub.model.User tempUser = new com.migia.OperationsHub.model.User();
        tempUser.setEmail(account.getEmail());
        tempUser.setMustChangePassword(false);

        String accessToken = jwtService.generateAccessToken(tempUser, org.getId(), Role.CUSTOMER);

        return LoginResponse.builder()
                .accessToken(accessToken)
                // Assuming no refresh token for customers for now, or adapt refreshTokenService to handle CustomerAccount
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .build();
    }
}
