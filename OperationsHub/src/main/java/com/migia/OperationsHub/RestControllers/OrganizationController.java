package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.dto.organization.UpdateOrganizationSettingsRequest;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{orgSlug}/api/v1/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_ORGANIZATION') or hasAuthority('PERM_MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Organization> getOrganizationSettings() {
        Organization org = organizationRepository.findById(TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        return ResponseEntity.ok(org);
    }

    @PatchMapping("/settings")
    @PreAuthorize("hasAuthority('PERM_MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Organization> updateOrganizationSettings(
            @Valid @RequestBody UpdateOrganizationSettingsRequest request) {
        Organization org = organizationRepository.findById(TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        if (request.getDefaultCurrency() != null) org.setDefaultCurrency(request.getDefaultCurrency());
        if (request.getLogoUrl() != null) org.setLogoUrl(request.getLogoUrl());
        if (request.getContactEmail() != null) org.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) org.setContactPhone(request.getContactPhone());
        if (request.getWebsite() != null) org.setWebsite(request.getWebsite());

        return ResponseEntity.ok(organizationRepository.save(org));
    }
}
