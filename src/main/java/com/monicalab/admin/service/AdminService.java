package com.monicalab.admin.service;

import com.monicalab.admin.entity.Admin;
import com.monicalab.admin.entity.AdminRole;
import com.monicalab.admin.repository.AdminRepository;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createInitialAdminIfAbsent(String loginId, String rawPassword, String name) {
        if (adminRepository.existsByLoginId(loginId)) {
            return;
        }

        Admin admin = Admin.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(rawPassword))
                .name(name)
                .role(AdminRole.ROLE_ADMIN)
                .build();

        adminRepository.save(admin);
        log.info("초기 관리자 계정을 생성했습니다. loginId={}", loginId);
    }

    @Transactional(readOnly = true)
    public Admin authenticate(String loginId, String rawPassword) {
        Admin admin = adminRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTHENTICATION_FAILED));

        if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
            throw new CustomException(ErrorCode.AUTHENTICATION_FAILED);
        }

        return admin;
    }

    @Transactional(readOnly = true)
    public Admin getById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }
}
