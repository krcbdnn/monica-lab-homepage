// @ts-check
const { test, expect } = require('@playwright/test');

// P15-T3: 관리자 로그인 rate limit(Nginx `limit_req`, rate=5r/m, burst=5, nodelay, 429)을 실제 운영 설정 그대로 검증한다.
// - 운영 계약 값은 테스트를 위해 완화하지 않는다. 대신 이 spec은 시간 기반으로 동작한다: 처음에 bucket이 비도록
//   75초 대기한 뒤(12초당 1건 회복 × 6) 즉시 허용 6건 → 7번째 429를 확인하고, 13초 뒤 1건만 다시 허용되는지 본다.
// - 존재하지 않는 계정으로 요청하므로 관리자 자격증명이 필요 없고 세션도 만들지 않는다(허용된 요청은 401).
// - 같은 클라이언트 IP의 로그인 요청을 소진하므로 다른 spec과 동시에/직후에 실행하지 않는다(단독 --workers=1).
// - HTTPS base URL(PLAYWRIGHT_BASE_URL=https://localhost:8443)로 실행한다. 앱은 별도로 기동되어 있어야 한다.

const LOGIN_API = '/api/admin/login';
const RATE_LIMIT_MESSAGE = '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.';
const PROBE = { loginId: 'p15-t3-rate-limit-probe', password: 'Not-a-real-password-1' };

async function attemptLogin(request) {
  return request.post(LOGIN_API, { data: PROBE, failOnStatusCode: false });
}

test.describe('P15-T3 관리자 로그인 rate limit(Nginx)', () => {
  test('즉시 6건까지 허용되고 7번째부터 Nginx가 429로 거절하며, 다른 경로는 영향이 없고 12초당 1건씩 회복된다',
    async ({ page, request, baseURL }) => {
      test.setTimeout(240000);

      // 이전 로그인 요청의 영향이 남지 않도록 bucket이 완전히 빌 때까지 기다린다(5r/m = 12초당 1건 회복, burst 5).
      await page.waitForTimeout(75000);

      // 1) 허용 범위: rate 1건 + burst 5건 = 즉시 6건은 Spring까지 전달된다(없는 계정이므로 401 JSON).
      for (let i = 1; i <= 6; i++) {
        const allowed = await attemptLogin(request);
        expect(allowed.status(), `${i}번째 로그인 요청은 허용되어야 한다`).toBe(401);
        expect((await allowed.json()).error.code).toBe('AUTHENTICATION_FAILED');
      }
      const lastAllowedAt = Date.now();

      // 2) 7번째: Nginx가 직접 429로 응답한다 - 본문은 ApiResponse JSON이 아니라 Nginx 기본 HTML이고 버전을 노출하지 않는다.
      const limited = await attemptLogin(request);
      expect(limited.status()).toBe(429);
      expect(limited.headers()['content-type']).toContain('text/html');
      const limitedBody = await limited.text();
      expect(limitedBody).toContain('429 Too Many Requests');
      expect(limitedBody).not.toContain('"success"');
      expect(limitedBody).not.toMatch(/nginx\/\d/);
      expect(limited.headers()['server']).toBe('nginx');
      expect(limited.headers()['strict-transport-security']).toBe('max-age=31536000');

      // 3) 제한은 정확히 /api/admin/login에만 걸린다 - 다른 관리자 API/화면, 공개 화면은 그대로 응답한다.
      expect((await request.get('/api/admin/me', { failOnStatusCode: false })).status()).toBe(401);
      expect((await request.get('/admin/login')).status()).toBe(200);
      expect((await request.get('/')).status()).toBe(200);
      expect((await request.get('/css/home.css')).status()).toBe(200);

      // 4) 로그인 화면은 429를 JSON 파싱 전에 판별해 전용 안내 문구를 표시한다.
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));
      await page.goto('/admin/login');
      await page.locator('#loginId').fill(PROBE.loginId);
      await page.locator('#password').fill(PROBE.password);
      const [uiResponse] = await Promise.all([
        page.waitForResponse((res) => res.url().endsWith(LOGIN_API)),
        page.locator('#loginForm button[type=submit]').click(),
      ]);
      expect(uiResponse.status()).toBe(429);
      await expect(page.locator('#errorMessage')).toHaveText(RATE_LIMIT_MESSAGE);
      await expect(page).toHaveURL(/\/admin\/login$/);
      expect(pageErrors).toEqual([]);

      // 5) 회복: 12초(5r/m)가 지나면 1건만 다시 허용되고, 곧바로 이어지는 요청은 다시 429다. 6번째 허용 후 24초가
      //    지나면 2건이 회복되므로, 경과 시간을 14~22초 사이로 맞춘 뒤 확인한다.
      await page.waitForTimeout(Math.max(0, 14000 - (Date.now() - lastAllowedAt)));
      expect(Date.now() - lastAllowedAt, '회복 확인 시점이 1건 회복 구간(12~24초) 안이어야 한다').toBeLessThan(22000);
      expect((await attemptLogin(request)).status()).toBe(401);
      expect((await attemptLogin(request)).status()).toBe(429);
    });
});
