package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.AdminOrganizationService;
import com.migia.OperationsHub.dto.admin.CreateOrganizationRequest;
import com.migia.OperationsHub.dto.admin.UpdateOrganizationStatusRequest;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.model.Organization;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/organizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminOrganizationController {

    private final AdminOrganizationService adminOrganizationService;

    @PostMapping
    public ResponseEntity<Organization> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        Organization org = adminOrganizationService.createOrganization(
                request.getName(), 
                request.getSlug(), 
                request.getOwnerEmail(), 
                request.getDefaultCurrency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(org);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<Organization>> getOrganizations(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminOrganizationService.getOrganizations(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organization> getOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(adminOrganizationService.getOrganization(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Organization> updateOrganizationStatus(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(adminOrganizationService.updateOrganizationStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable UUID id) {
        adminOrganizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}
