package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.CustomerAuthService;
import com.migia.OperationsHub.dto.auth.CustomerRegisterRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/{orgSlug}/api/v1/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody CustomerRegisterRequest request) {
        RegisterResponse response = customerAuthService.register(request, TenantContext.getCurrentTenant());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = customerAuthService.login(request, TenantContext.getCurrentTenant());
        return ResponseEntity.ok(response);
    }
}
