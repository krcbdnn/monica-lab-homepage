package com.monicalab.config;

import com.monicalab.common.exception.CustomAuthenticationEntryPoint;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // P3-T1(AdminPasswordConfig)에서 정의한 Bean을 그대로 주입받아 중복 정의를 방지한다.
    // 실제 인증(로그인) 로직에서의 사용은 P3-T3 범위.
    private final PasswordEncoder passwordEncoder;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/admin/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/login").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/admin/login"))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

    // /api/admin/** 인증 실패는 커스텀 UNAUTHORIZED JSON 응답, /admin/** 화면 경로는 로그인 화면으로 리다이렉트.
    private AuthenticationEntryPoint authenticationEntryPoint() {
        RequestMatcher apiAdminMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**");
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(apiAdminMatcher, customAuthenticationEntryPoint);

        DelegatingAuthenticationEntryPoint delegatingEntryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegatingEntryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/admin/login"));
        return delegatingEntryPoint;
    }
}
