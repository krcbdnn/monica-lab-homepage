package com.monicalab.admin.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monicalab.admin.entity.Admin;
import com.monicalab.admin.entity.AdminRole;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class AdminRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AdminRepository adminRepository;

    @Test
    void savingDuplicateLoginIdThrowsDataIntegrityViolationException() {
        adminRepository.saveAndFlush(Admin.builder()
                .loginId("duplicate-admin")
                .password("encoded-password-1")
                .name("관리자1")
                .role(AdminRole.ROLE_ADMIN)
                .build());

        Admin duplicate = Admin.builder()
                .loginId("duplicate-admin")
                .password("encoded-password-2")
                .name("관리자2")
                .role(AdminRole.ROLE_ADMIN)
                .build();

        assertThatThrownBy(() -> adminRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
