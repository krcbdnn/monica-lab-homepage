package com.monicalab.admin.config;

import com.monicalab.admin.service.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    private final AdminService adminService;
    private final String loginId;
    private final String password;
    private final String name;

    public AdminInitializer(AdminService adminService,
            @Value("${ADMIN_LOGIN_ID:}") String loginId,
            @Value("${ADMIN_PASSWORD:}") String password,
            @Value("${ADMIN_NAME:}") String name) {
        this.adminService = adminService;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(loginId) || !StringUtils.hasText(password) || !StringUtils.hasText(name)) {
            log.error("ADMIN_LOGIN_ID/ADMIN_PASSWORD/ADMIN_NAME 환경변수가 설정되지 않아 초기 관리자 계정을 생성하지 않습니다.");
            return;
        }

        adminService.createInitialAdminIfAbsent(loginId, password, name);
    }
}
