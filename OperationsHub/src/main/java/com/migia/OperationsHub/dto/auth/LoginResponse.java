package com.migia.OperationsHub.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private  String refreshToken;
    @Builder.Default
    private  String tokenType = "Bearer";
    private long expiresIn; // seconds
    private boolean mustChangePassword;
}
