package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.dto.auth.ChangePasswordRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.migia.OperationsHub.Security.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmailAndOrganizationIsNull(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        // We use null for orgId since platform admin belongs to no org
        String accessToken = jwtService.generateAdminToken(user);

        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .mustChangePassword(user.isMustChangePassword())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
  //  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal JwtPrincipal principal) {
        log.info("Change password request received");
        User user = userRepository.findByEmailAndOrganizationIsNull(principal.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid old password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }
}
