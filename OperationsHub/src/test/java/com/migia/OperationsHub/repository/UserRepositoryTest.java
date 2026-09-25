package com.migia.OperationsHub.repository;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice tests for {@link UserRepository}.
 * Uses @DataJpaTest to spin up an in-memory H2/embedded DB with only JPA
 * components loaded — fast and focused on repository-layer queries.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setupOrganizations() {
        orgA = organizationRepository.save(Organization.builder()
                .name("Org A")
                .slug("org-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Org B")
                .slug("org-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());
    }

    private User buildUser(String email, Organization org) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$hashed")
                .firstName("Test")
                .lastName("User")
                .role(Role.EMPLOYEE)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build();
    }

    /**
     * Test Case: Find by Email + Org — User Found
     * Verifies that findByEmailAndOrganization_Id returns the correct user
     * when a matching record exists for that (email, orgId) pair.
     */
    @Test
    void findByEmailAndOrganization_Id_returnsUser_whenPresent() {
        userRepository.save(buildUser("find@example.com", orgA));

        assertThat(userRepository.findByEmailAndOrganization_Id("find@example.com", orgA.getId()))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("find@example.com");
    }

    /**
     * Test Case: Find by Email + Org — User Not In That Org
     * Verifies that the same email registered in Org A is NOT returned
     * when queried against Org B. This tests the tenant isolation guarantee.
     */
    @Test
    void findByEmailAndOrganization_Id_returnsEmpty_whenEmailInDifferentOrg() {
        userRepository.save(buildUser("cross@example.com", orgA));

        // Same email, but queried against Org B — should return empty
        assertThat(userRepository.findByEmailAndOrganization_Id("cross@example.com", orgB.getId()))
                .isEmpty();
    }

    /**
     * Test Case: Exists by Email + Org — True When Found
     * Verifies that existsByEmailAndOrganization_Id returns true when a record exists.
     */
    @Test
    void existsByEmailAndOrganization_Id_returnsTrue_whenRecordExists() {
        userRepository.save(buildUser("exists@example.com", orgA));

        assertThat(userRepository.existsByEmailAndOrganization_Id("exists@example.com", orgA.getId()))
                .isTrue();
    }

    /**
     * Test Case: Exists by Email + Org — False When Absent
     * Verifies that existsByEmailAndOrganization_Id returns false for unknown combinations.
     */
    @Test
    void existsByEmailAndOrganization_Id_returnsFalse_whenRecordAbsent() {
        assertThat(userRepository.existsByEmailAndOrganization_Id("missing@example.com", orgA.getId()))
                .isFalse();
    }

    /**
     * Test Case: Unique Constraint — Same Email, Different Orgs
     * Verifies that the same email can coexist across different organizations
     * (the UNIQUE constraint is on the (organization_id, email) pair, not email alone).
     */
    @Test
    void save_sameEmailInDifferentOrgs_succeeds() {
        userRepository.save(buildUser("shared@example.com", orgA));
        userRepository.save(buildUser("shared@example.com", orgB));

        assertThat(userRepository.findByEmailAndOrganization_Id("shared@example.com", orgA.getId())).isPresent();
        assertThat(userRepository.findByEmailAndOrganization_Id("shared@example.com", orgB.getId())).isPresent();
    }

    /**
     * Test Case: Unique Constraint — Duplicate Email Within Same Org
     * Verifies that saving two users with the same email in the same organization
     * throws a DataIntegrityViolationException (enforced by the DB unique constraint).
     */
    @Test
    void save_duplicateEmailSameOrg_throwsException() {
        userRepository.saveAndFlush(buildUser("dup@example.com", orgA));

        assertThatThrownBy(() -> userRepository.saveAndFlush(buildUser("dup@example.com", orgA)))
                .isInstanceOf(Exception.class); // DataIntegrityViolationException
    }

    /**
     * Test Case: Find Platform Admin (null org)
     * Verifies that a platform admin (whose organization is null) can be found
     * via findByEmailAndOrganizationIsNull, and is NOT returned by org-scoped queries.
     */
    @Test
    void findByEmailAndOrganizationIsNull_returnsPlatformAdmin() {
        User admin = User.builder()
                .email("admin@platform.com")
                .passwordHash("$2a$hashed")
                .firstName("Platform")
                .lastName("Admin")
                .role(Role.PLATFORM_ADMIN)
                .organization(null) // Platform admin has no org
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
        userRepository.save(admin);

        assertThat(userRepository.findByEmailAndOrganizationIsNull("admin@platform.com"))
                .isPresent()
                .get()
                .extracting(User::getRole)
                .isEqualTo(Role.PLATFORM_ADMIN);

        // Admin should NOT appear in org-scoped query
        assertThat(userRepository.findByEmailAndOrganization_Id("admin@platform.com", orgA.getId()))
                .isEmpty();
    }
}
