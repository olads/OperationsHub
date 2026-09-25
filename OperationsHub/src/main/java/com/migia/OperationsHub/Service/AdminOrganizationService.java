package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrganizationService {

    private final OrganizationRepository organizationRepository;
    private final InvitationService invitationService;

    @Transactional
    public Organization createOrganization(String name, String slug, String ownerEmail, String defaultCurrency) {
        if (organizationRepository.existsBySlug(slug)) {
            throw new ConflictException("Organization with slug '" + slug + "' already exists");
        }

        Organization org = Organization.builder()
                .name(name)
                .slug(slug)
                .defaultCurrency(defaultCurrency)
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build();
        
        org = organizationRepository.save(org);

        // Seed owner invitation
        invitationService.createOwnerInvitation(ownerEmail, org);
        
        org.setOwnerInvitationSent(true);
        return organizationRepository.save(org);
    }

    public PagedResponse<Organization> getOrganizations(Pageable pageable) {
        Page<Organization> page = organizationRepository.findAll(pageable);
        return PagedResponse.from(page, org -> org);
    }

    public Organization getOrganization(UUID id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    @Transactional
    public Organization updateOrganizationStatus(UUID id, OrganizationStatus status) {
        Organization org = getOrganization(id);
        org.setStatus(status);
        return organizationRepository.save(org);
    }

    @Transactional
    public void deleteOrganization(UUID id) {
        Organization org = getOrganization(id);
        org.setStatus(OrganizationStatus.ARCHIVED);
        organizationRepository.save(org);
    }
}
