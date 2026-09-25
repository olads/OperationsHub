package com.migia.OperationsHub.Security;

import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-email:admin@operationshub.local}")
    private String defaultAdminEmail;

    @Value("${app.admin.default-password:ChangeMe123!}")
    private String defaultAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!userRepository.existsByEmailAndOrganizationIsNull(defaultAdminEmail)) {
            User admin = User.builder()
                    .email(defaultAdminEmail)
                    .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                    .firstName("Platform")
                    .lastName("Admin")
                    .role(Role.PLATFORM_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .mustChangePassword(true)
                    .build();
            userRepository.save(admin);
            log.warn("⚠️ Default platform admin created with email {}. Please login and change password immediately.", defaultAdminEmail);
        }
    }
}
