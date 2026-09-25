package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.InvitationService;
import com.migia.OperationsHub.dto.invitation.InvitationResponse;
import com.migia.OperationsHub.model.Invitation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping("/{token}")
    public ResponseEntity<InvitationResponse> getInvitationDetails(@PathVariable String token) {
        Invitation invitation = invitationService.getInvitationByToken(token);
        
        InvitationResponse response = InvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .token(invitation.getToken())
                .expiresAt(invitation.getExpiresAt())
                .organizationName(invitation.getOrganization().getName())
                .organizationSlug(invitation.getOrganization().getSlug())
                .build();
                
        return ResponseEntity.ok(response);
    }
}
