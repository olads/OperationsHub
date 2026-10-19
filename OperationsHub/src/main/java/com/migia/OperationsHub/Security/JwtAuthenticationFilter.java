package com.migia.OperationsHub.Security;

import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.model.enums.Permission;
import com.migia.OperationsHub.model.enums.Role;
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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stateless JWT authentication filter.
 *
 * <p>All information needed to authenticate and authorise a request is
 * self-contained inside the signed JWT (email, role, org). No database
 * lookup is performed here, so this filter works uniformly for every
 * principal type: org staff (User), customers (CustomerAccount),
 * platform admins, and any future user type.
 *
 * <p>The principal stored in the SecurityContext is a {@link JwtPrincipal}
 * record that downstream code can cast to when it needs the raw claims.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        System.out.println("authHeader = " + authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ") ) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        System.out.println("jwt = " + jwt);
        boolean setTenantContext = false;
        try {
            if (!jwtService.validateToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // ── 1. Extract all claims from the self-contained token ──────────────
            final String email        = jwtService.extractEmail(jwt);
            UUID   tokenOrgId   = jwtService.extractOrganizationId(jwt);
            final String roleName     = jwtService.extractRole(jwt);
            System.out.println("tokenOrgId = " + tokenOrgId);

            UUID   currentOrgId = TenantContext.getCurrentTenant();
            System.out.println("Tenant id " + currentOrgId);
            // ── 2. Cross-tenant guard ────────────────────────────────────────────
            // Both values must be present and must match.  Platform admins have no
            // org in their token (tokenOrgId == null) and only hit /api/v1/admin/**
            // paths where currentOrgId is also null, so they pass cleanly.
            if (currentOrgId != null && tokenOrgId != null && !currentOrgId.equals(tokenOrgId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token issued for a different tenant");
                return;
            }

            if (currentOrgId == null && tokenOrgId != null) {
                TenantContext.setCurrentTenant(tokenOrgId);
                setTenantContext = true;
            }

            if (!Role.PLATFORM_ADMIN.name().equals(roleName)) {
                // ── 3. Force password change ─────────────────────────────────────────
                Boolean mustChangePassword = jwtService.extractClaim(jwt, claims -> claims.get("mustChangePassword", Boolean.class));
                if (Boolean.TRUE.equals(mustChangePassword) && !request.getRequestURI().endsWith("/change-password")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
                    return;
                }
            }
            // ── 4. Build authorities from role (no DB touch) ─────────────────────
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                Role role = null;

                if (roleName != null) {
                    try {
                        role = Role.valueOf(roleName);
                        // ROLE_ prefix for hasRole() checks
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                        // PERM_ prefix for hasAuthority() checks used by @PreAuthorize
                        for (Permission permission : role.getPermissions()) {
                            authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name()));
                        }
                    } catch (IllegalArgumentException e) {
                        // Unknown role in token — leave authorities empty; request will
                        // be rejected by @PreAuthorize on the target endpoint.
                    }
                }

                // ── 5. Principal: a lightweight value object built from token claims ─
                // Using a Spring User with an empty password is fine here because we
                // are in a stateless JWT flow — credentials are never re-checked after
                // this point.  Downstream code that needs rich principal data can cast
                // the principal to JwtPrincipal.
                JwtPrincipal principal = new JwtPrincipal(email, tokenOrgId, role);

                System.out.println("Username Password Auth " + new UsernamePasswordAuthenticationToken(principal, null, authorities));
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            // Invalid / expired token — security context stays empty; Spring Security
            // will return 401 for any protected endpoint.
            filterChain.doFilter(request, response);
        } finally {
            if (setTenantContext) {
                TenantContext.clear();
            }
        }
    }
}
