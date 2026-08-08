package com.monicalab.admin.repository;

import com.monicalab.admin.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    boolean existsByLoginId(String loginId);

    Optional<Admin> findByLoginId(String loginId);
}
