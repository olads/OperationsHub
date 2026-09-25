package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.InvitationRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Invitation createInvitation(String email, Role role, UUID currentOrgId, String inviterEmail) {
        Organization org = organizationRepository.findById(currentOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        
        User inviter = userRepository.findByEmailAndOrganization_Id(inviterEmail, currentOrgId)
                .orElseGet(() -> userRepository.findByEmailAndOrganizationIsNull(inviterEmail)
                        .orElseThrow(() -> new ResourceNotFoundException("Inviter not found")));

        if (userRepository.existsByEmailAndOrganization_Id(email, currentOrgId)) {
            throw new ConflictException("User is already a member of this organization");
        }

        Optional<Invitation> existingPending = invitationRepository.findByEmailAndOrganization_IdAndAcceptedFalse(email, currentOrgId);
        if (existingPending.isPresent() && existingPending.get().getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("A pending invitation already exists for this email");
        }

        String token = generateSecureToken();
        Invitation invitation = Invitation.builder()
                .email(email)
                .organization(org)
                .inviter(inviter)
                .role(role)
                .token(token)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .accepted(false)
                .build();

        return invitationRepository.save(invitation);
    }
    
    @Transactional
    public Invitation createOwnerInvitation(String email, Organization org) {
        // Platform admin creates this, so inviter is null or we pass a platform admin if we want
        User platformAdmin = userRepository.findByEmailAndOrganizationIsNull("admin@operationshub.local").orElse(null);
        
        String token = generateSecureToken();
        Invitation invitation = Invitation.builder()
                .email(email)
                .organization(org)
                .inviter(platformAdmin) // Can be null if model allows, but let's assume it's allowed or we fetch the admin
                .role(Role.ORGANIZATION_OWNER)
                .token(token)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .accepted(false)
                .build();
                
        return invitationRepository.save(invitation);
    }

    public List<Invitation> getPendingInvitations(UUID currentOrgId) {
        return invitationRepository.findByOrganization_Id(currentOrgId);
    }

    public Invitation getInvitationByToken(String token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invitation token"));
    }
    
    @Transactional
    public void markAsAccepted(Invitation invitation) {
        invitation.setAccepted(true);
        invitationRepository.save(invitation);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
