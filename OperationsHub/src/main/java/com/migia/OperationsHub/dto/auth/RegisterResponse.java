package com.migia.OperationsHub.dto.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RegisterResponse {
    private UUID userId;
    private String email;
    private UUID organizationId;
    private String organizationSlug;
}
