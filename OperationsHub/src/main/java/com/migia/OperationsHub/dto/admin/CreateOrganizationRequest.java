package com.migia.OperationsHub.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrganizationRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String slug;
    
    @NotBlank
    @Email
    private String ownerEmail;
    
    private String defaultCurrency;
}
