CREATE TABLE site_theme_setting (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(50) NOT NULL,
    accent_preset VARCHAR(20) NOT NULL,
    show_pinned BOOLEAN NOT NULL,
    show_programs BOOLEAN NOT NULL,
    show_reviews BOOLEAN NOT NULL,
    show_notices BOOLEAN NOT NULL,
    show_gallery BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_site_theme_setting_setting_key UNIQUE (setting_key)
);

-- setting_key='SITE_THEME'는 이 테이블이 다루는 유일한 설정 그룹을 가리키는 내부 상수 값이다
-- (com.monicalab.theme.entity.SiteThemeSetting.SITE_THEME_KEY). 여러 종류의 설정을 저장하는
-- 범용 key-value 테이블이 아니라, "SITE_THEME"라는 단일 논리적 설정 1건만 가정한 구조이며
-- UNIQUE(setting_key)는 그 설정의 중복 저장만 막는다. P14-T3D 홈 화면의 실제 기본 동작(포인트
-- 컬러 없음/전 섹션 노출)과 동일한 값으로 시드한다.
INSERT INTO site_theme_setting
    (setting_key, accent_preset, show_pinned, show_programs, show_reviews, show_notices, show_gallery,
     created_at, updated_at)
VALUES
    ('SITE_THEME', 'TERRACOTTA', TRUE, TRUE, TRUE, TRUE, TRUE, NOW(), NOW());
