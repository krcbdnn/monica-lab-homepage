package com.monicalab.config;

import com.monicalab.common.exception.CustomAccessDeniedHandler;
import com.monicalab.common.exception.CustomAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // P3-T1(AdminPasswordConfig)에서 정의한 Bean을 그대로 주입받아 중복 정의를 방지한다.
    // 실제 인증(로그인) 로직에서의 사용은 P3-T3 범위.
    private final PasswordEncoder passwordEncoder;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

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
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/admin/login"))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler))
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

    // Spring Security 6.5 공식 SPA CSRF 연동 패턴(https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).
    // common-fetch.js가 XSRF-TOKEN 쿠키 값을 그대로 X-XSRF-TOKEN 헤더로 보내는 "raw cookie-to-header" 방식을 쓰므로,
    // 기본 XorCsrfTokenRequestAttributeHandler(마스킹된 값만 허용)만으로는 매 요청이 항상 실패한다.
    // 이 핸들러는 handle()에서 매 요청마다 deferred token을 강제로 로딩해 GET 응답에도 XSRF-TOKEN 쿠키가 내려가게 하고,
    // resolveCsrfTokenValue()에서 헤더로 온 값(=쿠키를 그대로 재전송한 값)은 plain 비교, 폼 파라미터로 온 값은
    // 여전히 BREACH 보호를 위해 xor 디코딩하도록 분기한다. CSRF 보호 자체를 끄거나 /api/admin/**를 예외 처리하지 않는다.
    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
                    .resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
