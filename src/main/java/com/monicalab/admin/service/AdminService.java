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

    // P15-T6: 로그인한 관리자 본인의 비밀번호 변경. 현재 비밀번호 불일치는 세션 만료(401)로 오인되지 않도록
    // INVALID_CURRENT_PASSWORD(400), 현재와 같은 새 비밀번호는 INVALID_INPUT_VALUE(400)로 거부한다.
    // 비밀번호 원문/hash는 로그와 예외 메시지에 남기지 않는다(성공 감사 로그도 두지 않는 기존 정책 유지).
    @Transactional
    public void changePassword(Long adminId, String currentPassword, String newPassword) {
        Admin admin = getById(adminId);

        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        admin.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional(readOnly = true)
    public Admin getById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }
}
