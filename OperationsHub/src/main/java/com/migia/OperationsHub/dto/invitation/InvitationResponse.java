package com.migia.OperationsHub.dto.invitation;

import com.migia.OperationsHub.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private UUID id;
    private String email;
    private Role role;
    private String token;
    private Instant expiresAt;
    private String organizationName;
    private String organizationSlug;
}
