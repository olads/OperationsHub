package com.migia.OperationsHub.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponse {
    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final List<OrganizationSummary> organizations;

    @Getter
    @Builder
    public static class OrganizationSummary {
        private final UUID id;
        private final String name;
        private final String slug;
        private final String role;
    }
}
