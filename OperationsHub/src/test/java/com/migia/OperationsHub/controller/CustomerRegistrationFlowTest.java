package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.CustomerRegisterRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 8 — Customer Self-Registration & Authentication.
 *
 * Verifies:
 *  - 8.1 Public registration under an organization → 201 Created
 *  - 8.2 Duplicate email in same org fails → 409 Conflict
 *  - 8.3 Invalid registration input (bad email) fails validation → 400 Bad Request
 *  - 8.4 Customer login with correct credentials → 200 OK + access token
 *  - 8.5 Customer login with wrong password fails → 4xx
 *  - 8.6 Customer login targeting wrong org slug fails → 404 Not Found
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerRegistrationFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;

    private Organization orgA;
    private Organization orgB;
    private String customerEmail;
    private String customerPassword;
    private UUID registeredCustomerId;
    private String customerToken;

    @BeforeAll
    void setup() {
        orgA = organizationRepository.save(Organization.builder()
                .name("Customer Flow Org A")
                .slug("cust-flow-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Customer Flow Org B")
                .slug("cust-flow-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        customerEmail = "customer-" + UUID.randomUUID().toString().substring(0, 6) + "@flow.com";
        customerPassword = "CustomerPass123!";
    }

    @Test
    @Order(1)
    void tc8_1_customerRegistration_returns201() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail(customerEmail);
        req.setPassword(customerPassword);
        req.setFirstName("Jane");
        req.setLastName("Customer");
        req.setPhone("+15551234567");

        MvcResult result = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(customerEmail))
                .andExpect(jsonPath("$.organizationSlug").value(orgA.getSlug()))
                .andReturn();

        RegisterResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), RegisterResponse.class);
        registeredCustomerId = resp.getUserId();
        assertThat(registeredCustomerId).isNotNull();
    }

    @Test
    @Order(2)
    void tc8_2_duplicateRegistration_returns409Conflict() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail(customerEmail);
        req.setPassword("AnotherPassword123!");
        req.setFirstName("Duplicate");
        req.setLastName("User");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void tc8_3_invalidRegistrationEmail_returns400BadRequest() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("not-an-email");
        req.setPassword("ValidPass123!");
        req.setFirstName("Invalid");
        req.setLastName("Email");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void tc8_4_customerLogin_returns200AndToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(customerEmail);
        req.setPassword(customerPassword);

        MvcResult result = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        LoginResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
        customerToken = resp.getAccessToken();
        assertThat(customerToken).isNotBlank();
    }

    @Test
    @Order(5)
    void tc8_5_customerLoginWrongPassword_fails() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(customerEmail);
        req.setPassword("WrongPassword!");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(6)
    void tc8_6_customerLoginWrongOrgSlug_returns404() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(customerEmail);
        req.setPassword(customerPassword);

        // Registered in Org A, but trying to log in under Org B
        mockMvc.perform(post("/" + orgB.getSlug() + "/api/v1/customer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
