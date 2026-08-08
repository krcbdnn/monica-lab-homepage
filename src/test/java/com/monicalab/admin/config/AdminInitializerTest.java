package com.monicalab.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.admin.entity.Admin;
import com.monicalab.admin.repository.AdminRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "ADMIN_LOGIN_ID=audit-test-admin",
        "ADMIN_PASSWORD=Passw0rd1",
        "ADMIN_NAME=테스트관리자"
})
class AdminInitializerTest extends AbstractIntegrationTest {

    private static final String TEST_LOGIN_ID = "audit-test-admin";
    private static final String TEST_PASSWORD = "Passw0rd1";

    @Autowired
    private AdminInitializer adminInitializer;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void applicationStartupCreatesExactlyOneAdminAndRerunDoesNotDuplicate() {
        List<Admin> createdOnStartup = findByLoginId(TEST_LOGIN_ID);
        assertThat(createdOnStartup).hasSize(1);

        Admin admin = createdOnStartup.get(0);
        assertThat(passwordEncoder.matches(TEST_PASSWORD, admin.getPassword())).isTrue();

        adminInitializer.run(new DefaultApplicationArguments());

        List<Admin> afterRerun = findByLoginId(TEST_LOGIN_ID);
        assertThat(afterRerun).hasSize(1);
        assertThat(afterRerun.get(0).getId()).isEqualTo(admin.getId());
    }

    private List<Admin> findByLoginId(String loginId) {
        return adminRepository.findAll().stream()
                .filter(a -> a.getLoginId().equals(loginId))
                .toList();
    }
}
