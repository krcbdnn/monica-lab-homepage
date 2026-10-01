package com.monicalab.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.support.AbstractIntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// P15-T1: reverse proxy(Nginx) 뒤 운영 runtime에서 X-Forwarded-Proto가 실제 embedded Tomcat/Spring Security
// 동작(쿠키 Secure/SameSite, login redirect, HSTS)까지 전달되는지 검증한다. 실제 Nginx/TLS가 아니라
// Spring/Tomcat의 forwarded header 인식을 검증하는 테스트다. 서버 설정 값은 application-prod.yml과 동일하게
// 명시하며, prod 파일에 실제로 그 값이 있는지는 ProdProfileConfigTest가 보장한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "server.tomcat.use-relative-redirects=true",
                "server.servlet.session.cookie.same-site=lax"
        })
class ForwardedHeaderIntegrationTest extends AbstractIntegrationTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int port;

    @Test
    void forwardedHttpsRequestGetsSecureCookiesAndRelativeLoginRedirectWithoutHsts() throws Exception {
        HttpResponse<String> response = get("/admin/dashboard", true);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("/admin/login");

        String sessionCookie = setCookie(response, "JSESSIONID");
        assertThat(sessionCookie).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(setCookie(response, "XSRF-TOKEN")).contains("Secure");

        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    }

    @Test
    void plainHttpRequestWithoutForwardedHeaderDoesNotGetSecureCookies() throws Exception {
        HttpResponse<String> response = get("/admin/dashboard", false);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("/admin/login");
        assertThat(setCookie(response, "JSESSIONID")).doesNotContain("Secure").contains("SameSite=Lax");
        assertThat(setCookie(response, "XSRF-TOKEN")).doesNotContain("Secure");
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void forwardedHttpsAdminApiRequestKeepsJsonUnauthorizedWithoutRedirect() throws Exception {
        HttpResponse<String> response = get("/api/admin/me", true);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    private HttpResponse<String> get(String path, boolean forwardedHttps) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (forwardedHttps) {
            builder.header("X-Forwarded-Proto", "https");
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String setCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie not found: " + name));
    }
}
