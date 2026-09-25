package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Service.CustomerService;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.customer.CreateCustomerRequest;
import com.migia.OperationsHub.dto.customer.CustomerResponse;
import com.migia.OperationsHub.dto.customer.UpdateCustomerRequest;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Customer;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomerService}.
 * Note: Customer is the business entity (B2B client of the org),
 * distinct from CustomerAccount (end-consumer who self-registers on the platform).
 * All dependencies are mocked — no Spring context or DB needed.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private CustomerService customerService;

    private UUID orgId;
    private Organization organization;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        organization = Organization.builder()
                .id(orgId)
                .name("Acme Corp")
                .slug("acme-corp")
                .build();
    }

    /**
     * Test Case: Create Customer — Success
     * Verifies that a new customer (B2B client) is saved and returns
     * ACTIVE status with correct name and email.
     */
    @Test
    void createCustomer_success() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .phone("123456789")
                .build();

        when(customerRepository.existsByOrganization_IdAndEmail(orgId, "jane@example.com")).thenReturn(false);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CustomerResponse response = customerService.createCustomer(request, orgId);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        verify(customerRepository).save(any(Customer.class));
    }

    /**
     * Test Case: Create Customer — Duplicate Email Within Same Org
     * Verifies that creating a customer with an email already registered
     * in the same org throws ConflictException and never saves.
     */
    @Test
    void createCustomer_duplicateEmail_throwsConflictException() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .build();

        when(customerRepository.existsByOrganization_IdAndEmail(orgId, "jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this organization");

        verify(customerRepository, never()).save(any());
    }

    /**
     * Test Case: Update Customer — Success
     * Verifies that an update request changes the customer's name, email, and phone.
     */
    @Test
    void updateCustomer_success() {
        UUID customerId = UUID.randomUUID();
        Customer existing = Customer.builder()
                .id(customerId)
                .organization(organization)
                .name("Old Name")
                .email("old@example.com")
                .status(CustomerStatus.ACTIVE)
                .build();

        UpdateCustomerRequest updateReq = UpdateCustomerRequest.builder()
                .name("New Name")
                .email("new@example.com")
                .phone("987654321")
                .status(CustomerStatus.ACTIVE)
                .build();

        when(customerRepository.findByIdAndOrganization_Id(customerId, orgId))
                .thenReturn(Optional.of(existing));
        when(customerRepository.existsByOrganization_IdAndEmailAndIdNot(orgId, "new@example.com", customerId))
                .thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.updateCustomer(customerId, updateReq, orgId);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getPhone()).isEqualTo("987654321");
    }

    /**
     * Test Case: Update Customer — Email Taken by Another Customer
     * Verifies that attempting to update to an email already owned by a different
     * customer in the same org throws ConflictException.
     */
    @Test
    void updateCustomer_duplicateEmail_throwsConflictException() {
        UUID customerId = UUID.randomUUID();
        Customer existing = Customer.builder()
                .id(customerId).organization(organization)
                .name("Old").email("old@example.com").build();

        UpdateCustomerRequest updateReq = UpdateCustomerRequest.builder()
                .name("New").email("taken@example.com").build();

        when(customerRepository.findByIdAndOrganization_Id(customerId, orgId))
                .thenReturn(Optional.of(existing));
        when(customerRepository.existsByOrganization_IdAndEmailAndIdNot(orgId, "taken@example.com", customerId))
                .thenReturn(true);

        assertThatThrownBy(() -> customerService.updateCustomer(customerId, updateReq, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this organization");
    }

    /**
     * Test Case: Get Customer — Not Found
     * Verifies that fetching a customer by ID that doesn't exist (or belongs to
     * a different org) throws ResourceNotFoundException.
     */
    @Test
    void getCustomer_notFound_throwsResourceNotFoundException() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdAndOrganization_Id(customerId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(customerId, orgId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    /**
     * Test Case: Deactivate Customer — Sets Status to INACTIVE
     * Verifies that deactivation correctly flips the customer status to INACTIVE.
     */
    @Test
    void deactivateCustomer_success() {
        UUID customerId = UUID.randomUUID();
        Customer existing = Customer.builder()
                .id(customerId).organization(organization)
                .name("Jane Doe").email("jane@example.com")
                .status(CustomerStatus.ACTIVE).build();

        when(customerRepository.findByIdAndOrganization_Id(customerId, orgId))
                .thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.deactivateCustomer(customerId, orgId);

        assertThat(response.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        verify(customerRepository).save(existing);
    }

    /**
     * Test Case: Search Customers — Returns Paged Response
     * Verifies that the search delegates correctly to the repository and
     * wraps results in a well-formed PagedResponse with metadata.
     */
    @Test
    void searchCustomers_returnsPagedResponse() {
        Customer c1 = Customer.builder().id(UUID.randomUUID()).name("Alice")
                .email("alice@test.com").status(CustomerStatus.ACTIVE).build();
        Customer c2 = Customer.builder().id(UUID.randomUUID()).name("Bob")
                .email("bob@test.com").status(CustomerStatus.ACTIVE).build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(c1, c2), pageable, 2);

        when(customerRepository.searchCustomers(orgId, "test", CustomerStatus.ACTIVE, pageable))
                .thenReturn(page);

        PagedResponse<CustomerResponse> response =
                customerService.searchCustomers("test", CustomerStatus.ACTIVE, pageable, orgId);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
    }
}
