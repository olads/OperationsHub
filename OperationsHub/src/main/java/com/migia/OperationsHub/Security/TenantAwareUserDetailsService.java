package com.migia.OperationsHub.Security;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

import com.migia.OperationsHub.tenancy.TenantContext;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantAwareUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        
        User user;
        if (currentOrgId != null) {
            user = userRepository.findByEmailAndOrganization_Id(email, currentOrgId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        } else {
            user = userRepository.findByEmailAndOrganizationIsNull(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Platform admin not found: " + email));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList() // Roles are extracted from the JWT token directly in the filter
        );
    }
}
