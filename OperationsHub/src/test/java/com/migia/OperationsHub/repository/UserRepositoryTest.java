package com.migia.OperationsHub.repository;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("opshub_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void existsByEmail_returnsTrue_whenEmailExists() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("$2a$hashed")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenEmailAbsent() {
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void findByEmail_returnsUser_whenPresent() {
        User user = User.builder()
                .email("find@example.com")
                .passwordHash("$2a$hashed")
                .firstName("Find")
                .lastName("Me")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        assertThat(userRepository.findByEmail("find@example.com"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("find@example.com");
    }

    @Test
    void save_duplicateEmail_throwsException() {
        User user1 = User.builder()
                .email("dup@example.com")
                .passwordHash("$2a$hashed")
                .firstName("A").lastName("B")
                .status(UserStatus.ACTIVE).build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .email("dup@example.com")
                .passwordHash("$2a$other")
                .firstName("C").lastName("D")
                .status(UserStatus.ACTIVE).build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(Exception.class);
    }
}
