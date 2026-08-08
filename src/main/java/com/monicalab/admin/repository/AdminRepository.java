package com.monicalab.admin.repository;

import com.monicalab.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    boolean existsByLoginId(String loginId);
}
