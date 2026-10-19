package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.customer.CreateCustomerRequest;
import com.migia.OperationsHub.dto.customer.CustomerResponse;
import com.migia.OperationsHub.dto.customer.UpdateCustomerRequest;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 9 — Customer Management (Back-Office View).
 *
 * Verifies:
 *  - 9.1 Create customer manually (back-office) → 201 Created
 *  - 9.2 Duplicate email in same org fails → 409 Conflict
 *  - 9.3 List all customers (paged) → 200 OK
 *  - 9.4 Search customers by name or email query → 200 OK
 *  - 9.5 Filter customers by status (ACTIVE) → 200 OK
 *  - 9.6 Get single customer by ID → 200 OK
 *  - 9.7 Update customer details → 200 OK
 *  - 9.8 Deactivate customer → 200 OK, status = INACTIVE
 *  - 9.9 Employee (view-only) can list customers → 200 OK
 *  - 9.10 Employee cannot create customers → 403 Forbidden
 *  - 9.11 Delete customer (soft deactivate) → 204 No Content
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerBackOfficeFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;

    private Organization org;
    private String ownerToken;
    private String employeeToken;
    private UUID customerId;
    private String customerEmail;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Cust Backoffice Corp")
                .slug("cust-bo-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        userRepository.save(User.builder()
                .email("bo-owner@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Owner")
                .lastName("Bo")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        userRepository.save(User.builder()
                .email("bo-employee@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Employee")
                .lastName("Bo")
                .role(Role.EMPLOYEE)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = loginAs("bo-owner@flow.com", "Pass@1!", org.getSlug());
        employeeToken = loginAs("bo-employee@flow.com", "Pass@1!", org.getSlug());

        customerEmail = "client-" + UUID.randomUUID().toString().substring(0, 6) + "@domain.com";
    }

    private String loginAs(String email, String pass, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(pass);

        MvcResult r = mockMvc.perform(post("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    @Test
    @Order(1)
    void tc9_1_createCustomer_returns201() throws Exception {
        CreateCustomerRequest req = CreateCustomerRequest.builder()
                .name("Robert Client")
                .email(customerEmail)
                .phone("+14155552671")
                .build();

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Robert Client"))
                .andExpect(jsonPath("$.email").value(customerEmail))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        CustomerResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), CustomerResponse.class);
        customerId = resp.getId();
        assertThat(customerId).isNotNull();
    }

    @Test
    @Order(2)
    void tc9_2_createCustomerDuplicateEmail_returns409Conflict() throws Exception {
        CreateCustomerRequest req = CreateCustomerRequest.builder()
                .name("Duplicate Robert")
                .email(customerEmail)
                .phone("+14155552672")
                .build();

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void tc9_3_listCustomers_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(4)
    void tc9_4_searchCustomersByQuery_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/customers?q=Robert")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Robert Client"));
    }

    @Test
    @Order(5)
    void tc9_5_filterCustomersByStatus_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/customers?status=ACTIVE")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(6)
    void tc9_6_getSingleCustomer_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.name").value("Robert Client"));
    }

    @Test
    @Order(7)
    void tc9_7_updateCustomer_returns200() throws Exception {
        UpdateCustomerRequest req = UpdateCustomerRequest.builder()
                .name("Robert Updated")
                .email(customerEmail)
                .phone("+14155559999")
                .status(CustomerStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/" + org.getSlug() + "/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Robert Updated"))
                .andExpect(jsonPath("$.phone").value("+14155559999"));
    }

    @Test
    @Order(8)
    void tc9_8_deactivateCustomer_returns200WithInactive() throws Exception {
        mockMvc.perform(patch("/" + org.getSlug() + "/api/v1/customers/" + customerId + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @Order(9)
    void tc9_9_employeeCanListCustomers_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    void tc9_10_employeeCannotCreateCustomer_returns403() throws Exception {
        CreateCustomerRequest req = CreateCustomerRequest.builder()
                .name("Blocked Customer")
                .email("blocked@domain.com")
                .build();

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void tc9_11_deleteCustomer_returns204() throws Exception {
        mockMvc.perform(delete("/" + org.getSlug() + "/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }
}
