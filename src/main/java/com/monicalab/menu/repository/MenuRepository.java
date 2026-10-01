package com.monicalab.menu.repository;

import com.monicalab.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    boolean existsByParentId(Long parentId);
}
