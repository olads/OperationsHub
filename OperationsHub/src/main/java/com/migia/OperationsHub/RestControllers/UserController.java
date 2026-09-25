package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.UserProfileResponse;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{orgSlug}/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Returns the authenticated user's profile scoped to the current tenant.
     * TenantContext.getCurrentTenant() is set by TenantResolutionFilter — no org list
     * is returned so cross-tenant information cannot be leaked.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserDetails principal) {
        String email = principal.getUsername();
        UUID currentOrgId = TenantContext.getCurrentTenant();

        User user = userRepository.findByEmailAndOrganization_Id(email, currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found in this organization"));

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(profile);
    }
}
