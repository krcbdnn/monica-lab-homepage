package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

// P15-T1: prod profile의 실효 설정(공통 application.yml + application-prod.yml, profile 파일이 우선)을
// Spring context/Testcontainers 없이 검증한다. Spring Boot와 동일하게 profile-specific 파일이 공통 파일보다
// 우선하도록 PropertySource 순서만 구성하며, 환경변수 override는 이 테스트의 대상이 아니다.
class ProdProfileConfigTest {

    private static PropertyResolver prodResolver;

    @BeforeAll
    static void loadProdEffectiveConfig() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources sources = new MutablePropertySources();
        load(loader, "application-prod.yml").forEach(sources::addLast);
        load(loader, "application.yml").forEach(sources::addLast);
        prodResolver = new PropertySourcesPropertyResolver(sources);
    }

    private static List<PropertySource<?>> load(YamlPropertySourceLoader loader, String name) throws IOException {
        return loader.load(name, new ClassPathResource(name));
    }

    @Test
    void prodDisablesSqlLoggingInheritedFromCommonConfig() {
        assertThat(prodResolver.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(prodResolver.getProperty("spring.jpa.properties.hibernate.format_sql", Boolean.class)).isFalse();
    }

    @Test
    void prodAppliesReverseProxyRuntimeContract() {
        assertThat(prodResolver.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(prodResolver.getProperty("server.tomcat.use-relative-redirects", Boolean.class)).isTrue();
        assertThat(prodResolver.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    }

    @Test
    void prodKeepsExistingSchemaValidationAndHealthOnlyExposure() {
        assertThat(prodResolver.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(prodResolver.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    }
}
