package com.monicalab.admin.dto;

import com.monicalab.admin.entity.Admin;
import com.monicalab.admin.entity.AdminRole;

public record AdminResponse(Long id, String loginId, String name, AdminRole role) {

    public static AdminResponse from(Admin admin) {
        return new AdminResponse(admin.getId(), admin.getLoginId(), admin.getName(), admin.getRole());
    }
}
