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

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private FilterChain filterChain;
    @InjectMocks private TenantResolutionFilter filter;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void knownSlug_setsTenantContextAndProceedsChain() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = Organization.builder()
                .name("Acme Corp").slug("acme").status(OrganizationStatus.ACTIVE).build();
        org = spy(org);
        doReturn(orgId).when(org).getId();

        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/acme/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unknownSlug_returns404AndBlocksChain() throws Exception {
        when(organizationRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(filterChain);
    }

    @Test
    void nonTenantPath_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void tenantContext_isAlwaysClearedAfterRequest() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = spy(Organization.builder()
                .name("Acme").slug("acme").status(OrganizationStatus.ACTIVE).build());
        doReturn(orgId).when(org).getId();
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/acme/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // After filter completes, TenantContext must be empty
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
