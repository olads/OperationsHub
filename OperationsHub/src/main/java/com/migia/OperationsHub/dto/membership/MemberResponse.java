package com.migia.OperationsHub.dto.membership;

import com.migia.OperationsHub.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
}
