package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserRepository userRepository;

    public PagedResponse<User> getEmployees(UUID currentOrgId, Pageable pageable) {
        Page<User> page = userRepository.findByOrganization_Id(currentOrgId, pageable);
        return PagedResponse.from(page, user -> user);
    }

    public User getEmployee(UUID id, UUID currentOrgId) {
        return userRepository.findById(id)
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(currentOrgId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    @Transactional
    public User updateEmployeeRole(UUID id, Role role, UUID currentOrgId) {
        User employee = getEmployee(id, currentOrgId);
        
        // Ensure you can only set roles valid for an org
        if (role == Role.PLATFORM_ADMIN || role == Role.CUSTOMER) {
            throw new IllegalArgumentException("Invalid role for employee");
        }
        
        employee.setRole(role);
        return userRepository.save(employee);
    }
}
