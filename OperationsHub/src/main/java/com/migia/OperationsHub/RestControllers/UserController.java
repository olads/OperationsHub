package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Repository.MembershipRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.UserProfileResponse;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Membership;
import com.migia.OperationsHub.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserDetails principal) {
        String email = principal.getUsername();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Membership> memberships = membershipRepository.findByUser_Email(email);

        List<UserProfileResponse.OrganizationSummary> orgSummaries = memberships.stream()
                .map(m -> UserProfileResponse.OrganizationSummary.builder()
                        .id(m.getOrganization().getId())
                        .name(m.getOrganization().getName())
                        .slug(m.getOrganization().getSlug())
                        .role(m.getRole().name())
                        .build())
                .toList();

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .organizations(orgSummaries)
                .build();

        return ResponseEntity.ok(profile);
    }
}
