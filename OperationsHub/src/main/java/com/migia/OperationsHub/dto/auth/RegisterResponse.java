package com.migia.OperationsHub.dto.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RegisterResponse {
    private final UUID userId;
    private final String email;
    private final UUID organizationId;
    private final String organizationSlug;
}
