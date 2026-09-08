package com.migia.OperationsHub.tenancy;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.model.Organization;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts every request matching /{orgSlug}/api/v1/**.
 * Resolves the slug to an Organization and stores the organizationId
 * in TenantContext for the duration of the request.
 *
 * ALWAYS clears TenantContext in the finally block to prevent leakage
 * across requests on reused thread-pool threads.
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    // Matches /{slug}/api/v1/... and captures the slug
    private static final Pattern TENANT_PATH_PATTERN =
            Pattern.compile("^/([^/]+)/api/v1(/.*)?$");

    private final OrganizationRepository organizationRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Matcher matcher = TENANT_PATH_PATTERN.matcher(path);

        if (!matcher.matches()) {
            // Path doesn't match the tenant pattern — let it through
            // (e.g. actuator/health, swagger, etc.)
            filterChain.doFilter(request, response);
            return;
        }

        String slug = matcher.group(1);

        try {
            Optional<Organization> org = organizationRepository.findBySlug(slug);

            if (org.isEmpty()) {
                // Return 404 — deliberately generic to avoid tenant enumeration
                sendNotFound(response, slug);
                return;
            }

            TenantContext.setCurrentTenant(org.get().getId());
            filterChain.doFilter(request, response);

        } finally {
            // Critical: always clear to prevent tenant bleed across requests
            TenantContext.clear();
        }
    }

    private void sendNotFound(HttpServletResponse response, String slug) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Unknown organization: '" + slug + "'\"}"
        );
    }
}
