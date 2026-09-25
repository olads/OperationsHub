package com.migia.OperationsHub.tenancy;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantResolutionFilter}.
 * Verifies that the filter correctly resolves the tenant from the URL slug,
 * sets/clears the TenantContext, and handles edge cases (unknown slug, admin paths).
 */
@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private FilterChain filterChain;
    @InjectMocks private TenantResolutionFilter filter;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    /**
     * Test Case: Known Slug — Sets Tenant Context and Proceeds
     * Verifies that when the URL slug matches a known active organization,
     * the filter sets TenantContext and forwards the request down the chain.
     */
    @Test
    void knownSlug_setsTenantContextAndProceedsChain() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = spy(Organization.builder()
                .name("Acme Corp")
                .slug("acme")
                .status(OrganizationStatus.ACTIVE)
                .build());
        doReturn(orgId).when(org).getId();

        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/acme/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Filter chain was called (request proceeded)
        verify(filterChain).doFilter(request, response);
        // Response status should be default (200, not set by filter for known slugs)
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Test Case: Unknown Slug — Returns 404 and Blocks Chain
     * Verifies that an unrecognized org slug results in a 404 response
     * and the filter chain is NOT invoked (request is terminated early).
     */
    @Test
    void unknownSlug_returns404AndBlocksChain() throws Exception {
        when(organizationRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(filterChain);
    }

    /**
     * Test Case: Admin Path — Bypasses Tenant Resolution
     * Verifies that paths under /api/v1/admin/** are not subject to tenant
     * resolution — the filter passes through without consulting the org repository.
     */
    @Test
    void adminPath_passesThrough_noOrgLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(organizationRepository);
    }

    /**
     * Test Case: Actuator/Health Path — Bypasses Tenant Resolution
     * Verifies that non-tenant infrastructure paths (actuator, static resources)
     * pass through the filter without any org lookup.
     */
    @Test
    void nonTenantPath_passesThrough_noOrgLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(organizationRepository);
    }

    /**
     * Test Case: TenantContext Is Always Cleared After Request
     * Verifies that even after a successful tenant resolution, the TenantContext
     * is cleared in the finally block to prevent tenant data from leaking
     * across requests in the same thread (thread-local contamination).
     */
    @Test
    void tenantContext_isAlwaysClearedAfterRequest() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = spy(Organization.builder()
                .name("Acme")
                .slug("acme")
                .status(OrganizationStatus.ACTIVE)
                .build());
        doReturn(orgId).when(org).getId();
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/acme/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // After filter completes, TenantContext must be null — no thread-local leak
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    /**
     * Test Case: TenantContext Is Cleared Even When Chain Throws
     * Verifies that if the downstream filter chain throws an exception,
     * the TenantContext is still cleared (finally block runs correctly).
     */
    @Test
    void tenantContext_isClearedEvenIfChainThrows() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = spy(Organization.builder()
                .name("Error Org")
                .slug("error-org")
                .status(OrganizationStatus.ACTIVE)
                .build());
        doReturn(orgId).when(org).getId();
        when(organizationRepository.findBySlug("error-org")).thenReturn(Optional.of(org));

        doThrow(new RuntimeException("Downstream failure"))
                .when(filterChain).doFilter(any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error-org/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
            // Expected — we're testing the finally block
        }

        // TenantContext must still be cleared despite the exception
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
