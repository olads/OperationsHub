package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.CustomerService;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.customer.CreateCustomerRequest;
import com.migia.OperationsHub.dto.customer.CustomerResponse;
import com.migia.OperationsHub.dto.customer.UpdateCustomerRequest;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import com.migia.OperationsHub.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{orgSlug}/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_CUSTOMERS')")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        CustomerResponse response = customerService.createCustomer(request, currentOrgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_CUSTOMERS')")
    public ResponseEntity<PagedResponse<CustomerResponse>> getCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        PagedResponse<CustomerResponse> response = customerService.searchCustomers(q, status, pageable, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_CUSTOMERS')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        CustomerResponse response = customerService.getCustomer(id, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_CUSTOMERS')")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        CustomerResponse response = customerService.updateCustomer(id, request, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_MANAGE_CUSTOMERS')")
    public ResponseEntity<CustomerResponse> deactivateCustomer(@PathVariable UUID id) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        CustomerResponse response = customerService.deactivateCustomer(id, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_CUSTOMERS')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        customerService.deactivateCustomer(id, currentOrgId);
        return ResponseEntity.noContent().build();
    }
}
