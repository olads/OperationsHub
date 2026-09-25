package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.EmployeeService;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/{orgSlug}/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_EMPLOYEES')")
    public ResponseEntity<PagedResponse<User>> getEmployees(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(employeeService.getEmployees(TenantContext.getCurrentTenant(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_EMPLOYEES')")
    public ResponseEntity<User> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getEmployee(id, TenantContext.getCurrentTenant()));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('PERM_MANAGE_EMPLOYEES')")
    public ResponseEntity<User> updateEmployeeRole(@PathVariable UUID id, @RequestParam Role role) {
        return ResponseEntity.ok(employeeService.updateEmployeeRole(id, role, TenantContext.getCurrentTenant()));
    }
}
