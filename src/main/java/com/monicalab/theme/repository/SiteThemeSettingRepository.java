package com.monicalab.theme.repository;

import com.monicalab.theme.entity.SiteThemeSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteThemeSettingRepository extends JpaRepository<SiteThemeSetting, Long> {

    Optional<SiteThemeSetting> findBySettingKey(String settingKey);
}
