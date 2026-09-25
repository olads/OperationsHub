package com.migia.OperationsHub.Security;

import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.Permission;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            if (jwtService.validateToken(jwt)) {
                userEmail = jwtService.extractEmail(jwt);
                UUID tokenOrgId = jwtService.extractOrganizationId(jwt);
                UUID currentOrgId = TenantContext.getCurrentTenant();

                    // Cross-tenant validation: ensure the token was issued for the organization being accessed
                    // Platform admins have null tokenOrgId, allow them if currentOrgId is present (they can access any org if we allow them, or they only access admin APIs)
                    // Wait, platform admins don't pass through TenantResolutionFilter because they use /api/v1/admin, so currentOrgId is null.
                    if (currentOrgId != null && tokenOrgId != null && !currentOrgId.equals(tokenOrgId)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token issued for a different tenant");
                        return;
                    }
                    
                    Boolean mustChangePassword = jwtService.extractClaim(jwt, claims -> claims.get("mustChangePassword", Boolean.class));
                    if (Boolean.TRUE.equals(mustChangePassword) && !request.getRequestURI().endsWith("/change-password")) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
                        return;
                    }

                    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                        // Extract role from token and build authorities
                        String roleName = jwtService.extractClaim(jwt, claims -> claims.get("role", String.class));
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        if (roleName != null) {
                            try {
                                Role role = Role.valueOf(roleName);
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                                for (Permission permission : role.getPermissions()) {
                                    authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name()));
                                }
                            } catch (IllegalArgumentException e) {
                                // Invalid role in token, default to empty
                            }
                        }

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                authorities
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
            }
        } catch (Exception e) {
            // Log the exception if needed, but don't authenticate the user
        }

        filterChain.doFilter(request, response);
    }
}
