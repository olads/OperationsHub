package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.customer.CreateCustomerRequest;
import com.migia.OperationsHub.dto.customer.CustomerResponse;
import com.migia.OperationsHub.dto.customer.UpdateCustomerRequest;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Customer;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request, UUID orgId) {
        if (customerRepository.existsByOrganization_IdAndEmail(orgId, request.getEmail())) {
            throw new ConflictException("Customer with email '" + request.getEmail() + "' already exists in this organization");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Customer customer = Customer.builder()
                .organization(org)
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        return toCustomerResponse(saved);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request, UUID orgId) {
        Customer customer = customerRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (customerRepository.existsByOrganization_IdAndEmailAndIdNot(orgId, request.getEmail(), id)) {
            throw new ConflictException("Customer with email '" + request.getEmail() + "' already exists in this organization");
        }

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        if (request.getStatus() != null) {
            customer.setStatus(request.getStatus());
        }

        Customer updated = customerRepository.save(customer);
        return toCustomerResponse(updated);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id, UUID orgId) {
        Customer customer = customerRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toCustomerResponse(customer);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> searchCustomers(String query, CustomerStatus status, Pageable pageable, UUID orgId) {
        Page<Customer> page = customerRepository.searchCustomers(orgId, query, status, pageable);
        return PagedResponse.from(page, this::toCustomerResponse);
    }

    @Transactional
    public CustomerResponse deactivateCustomer(UUID id, UUID orgId) {
        Customer customer = customerRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        customer.setStatus(CustomerStatus.INACTIVE);
        Customer saved = customerRepository.save(customer);
        return toCustomerResponse(saved);
    }

    private CustomerResponse toCustomerResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .status(customer.getStatus())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
