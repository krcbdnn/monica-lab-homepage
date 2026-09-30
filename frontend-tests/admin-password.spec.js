// @ts-check
const crypto = require('crypto');
const { test, expect, request: playwrightRequest } = require('@playwright/test');

// P15-T6: 관리자 본인 비밀번호 변경 화면(/admin/password) 실제 브라우저 검증.
// - 관리자 로그인 자격증명은 다른 admin spec과 같은 환경변수(ADMIN_LOGIN_ID/ADMIN_PASSWORD)에서 읽는다.
// - 성공 경로는 실제 관리자 비밀번호를 임시 값으로 바꾸므로 finally에서 API로 원래 비밀번호로 되돌리고, 원래
//   비밀번호 로그인이 다시 성공하는지까지 검증한다. 임시/원래 비밀번호는 어떤 로그/리포트에도 출력하지 않는다.
// - 비밀번호가 바뀌어 있는 동안 다른 spec이 로그인하면 실패하므로 이 spec은 단독(--workers=1)으로 실행한다.
// - 400 응답을 의도적으로 받는 오류 경로에서는 브라우저가 남기는 "Failed to load resource" 네트워크 console
//   메시지만 제외하고, 그 외 console error와 pageerror는 허용하지 않는다.
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 하며, CI에는 포함되지 않는 수동 실행 대상이다.

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const PASSWORD_API = '/api/admin/me/password';

// AdminPasswordChangeRequest.NEW_PASSWORD_PATTERN + 8~64자와 같은 정책(원복 가능 여부 판단용).
const NEW_PASSWORD_POLICY = /^(?:(?=.*[A-Za-z])(?=.*[0-9])|(?=.*[A-Za-z])(?=.*[!-/:-@[-`{-~])|(?=.*[0-9])(?=.*[!-/:-@[-`{-~]))[!-~]{8,64}$/;

function temporaryPassword() {
  // 영문/숫자/특수문자를 모두 포함한 ASCII 임시 비밀번호. 실행마다 달라 원래 비밀번호와 겹치지 않는다.
  return `T6a9!${crypto.randomBytes(12).toString('base64url')}`;
}

function collectErrors(page, { ignoreNetworkErrors = false } = {}) {
  const errors = { pageErrors: [], consoleErrors: [] };
  page.on('pageerror', (error) => errors.pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return;
    }
    if (ignoreNetworkErrors && message.text().startsWith('Failed to load resource')) {
      return;
    }
    errors.consoleErrors.push(message.text());
  });
  return errors;
}

async function loginAsAdmin(page, baseURL) {
  const response = await page.context().request.post(`${baseURL}/api/admin/login`, {
    data: { loginId: ADMIN_LOGIN_ID, password: ADMIN_PASSWORD },
  });
  expect(response.ok(), '관리자 로그인 실패 - ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수를 확인하세요').toBeTruthy();
}

async function fillPasswordForm(page, { current, next, confirm }) {
  await page.locator('#currentPassword').fill(current);
  await page.locator('#newPassword').fill(next);
  await page.locator('#newPasswordConfirm').fill(confirm);
}

// 별도 API 세션으로 로그인만 시도해 status를 돌려준다(page 세션과 독립).
async function loginStatus(baseURL, password) {
  const context = await playwrightRequest.newContext({ baseURL });
  try {
    const response = await context.post('/api/admin/login', { data: { loginId: ADMIN_LOGIN_ID, password } });
    return response.status();
  } finally {
    await context.dispose();
  }
}

// 별도 API 세션으로 from → to 비밀번호 변경(CSRF 헤더 포함). 성공 여부만 돌려준다.
async function changePasswordViaApi(baseURL, from, to) {
  const context = await playwrightRequest.newContext({ baseURL });
  try {
    const login = await context.post('/api/admin/login', { data: { loginId: ADMIN_LOGIN_ID, password: from } });
    if (!login.ok()) {
      return false;
    }
    const { cookies } = await context.storageState();
    const xsrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
    if (!xsrf) {
      return false;
    }
    const response = await context.put(PASSWORD_API, {
      headers: { 'X-XSRF-TOKEN': xsrf.value },
      data: { currentPassword: from, newPassword: to },
    });
    return response.ok();
  } finally {
    await context.dispose();
  }
}

test.describe('P15-T6 관리자 비밀번호 변경', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test('header의 비밀번호 변경 링크로 진입하면 3개 입력과 정책 안내가 보인다', async ({ page, baseURL }) => {
    const errors = collectErrors(page);
    await loginAsAdmin(page, baseURL);
    await page.goto('/admin/dashboard');

    const link = page.locator('#admin-header #admin-password-link');
    await expect(link).toBeVisible();
    await expect(link).toHaveText('비밀번호 변경');
    await Promise.all([page.waitForURL('**/admin/password'), link.click()]);

    await expect(page.locator('#passwordForm input[type=password]')).toHaveCount(3);
    await expect(page.locator('#currentPassword')).toHaveAttribute('autocomplete', 'current-password');
    await expect(page.locator('#newPassword')).toHaveAttribute('autocomplete', 'new-password');
    await expect(page.locator('#newPasswordConfirm')).toHaveAttribute('autocomplete', 'new-password');
    await expect(page.locator('#newPasswordHelp')).toContainText('8자 이상 64자 이하');
    await expect(page.locator('#admin-logout-button')).toBeVisible();
    await page.waitForLoadState('networkidle');

    expect(errors.pageErrors, `pageerror: ${errors.pageErrors.join(', ')}`).toEqual([]);
    expect(errors.consoleErrors, `console error: ${errors.consoleErrors.join(', ')}`).toEqual([]);
  });

  test('새 비밀번호 확인이 다르면 API를 호출하지 않고 오류를 표시한다', async ({ page, baseURL }) => {
    const errors = collectErrors(page);
    const passwordRequests = [];
    page.on('request', (req) => {
      if (req.url().endsWith(PASSWORD_API)) {
        passwordRequests.push(req.method());
      }
    });
    await loginAsAdmin(page, baseURL);
    await page.goto('/admin/password');

    await fillPasswordForm(page, { current: 'Anything#1', next: 'Mismatch#11', confirm: 'Mismatch#22' });
    await page.locator('#saveButton').click();

    await expect(page.locator('#errorMessage')).toHaveText('새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다.');
    await expect(page.locator('#successMessage')).toBeHidden();
    expect(passwordRequests).toEqual([]);
    expect(errors.pageErrors).toEqual([]);
    expect(errors.consoleErrors).toEqual([]);
  });

  test('현재 비밀번호가 틀리면 서버 오류를 표시하고 로그인 세션은 유지된다', async ({ page, baseURL }) => {
    const errors = collectErrors(page, { ignoreNetworkErrors: true });
    await loginAsAdmin(page, baseURL);
    await page.goto('/admin/password');

    await fillPasswordForm(page, { current: 'Wrong#Current9', next: 'Valid#Next123', confirm: 'Valid#Next123' });
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().endsWith(PASSWORD_API)),
      page.locator('#saveButton').click(),
    ]);

    expect(response.status()).toBe(400);
    await expect(page.locator('#errorMessage')).toHaveText('현재 비밀번호가 올바르지 않습니다.');
    await expect(page).toHaveURL(/\/admin\/password$/);
    expect((await page.context().request.get(`${baseURL}/api/admin/me`)).status()).toBe(200);
    expect(await loginStatus(baseURL, ADMIN_PASSWORD)).toBe(200);
    expect(errors.pageErrors).toEqual([]);
    expect(errors.consoleErrors).toEqual([]);
  });

  test('정책을 위반한 새 비밀번호는 서버 정책 문구를 표시한다', async ({ page, baseURL }) => {
    const errors = collectErrors(page, { ignoreNetworkErrors: true });
    await loginAsAdmin(page, baseURL);
    await page.goto('/admin/password');

    // 현재 비밀번호를 일부러 틀리게 둔다 - Validation이 먼저 실패하므로 비밀번호가 바뀔 위험이 없다.
    for (const invalid of ['abcdefghij', '비밀번호abc123']) {
      await fillPasswordForm(page, { current: 'Wrong#Current9', next: invalid, confirm: invalid });
      const [response] = await Promise.all([
        page.waitForResponse((res) => res.url().endsWith(PASSWORD_API)),
        page.locator('#saveButton').click(),
      ]);
      expect(response.status()).toBe(400);
      await expect(page.locator('#errorMessage')).toContainText('영문/숫자/특수문자 중 2종 이상');
    }

    expect(await loginStatus(baseURL, ADMIN_PASSWORD)).toBe(200);
    expect(errors.pageErrors).toEqual([]);
    expect(errors.consoleErrors).toEqual([]);
  });

  test('375px에서 header와 비밀번호 변경 화면에 가로 overflow가 없다', async ({ page, baseURL }) => {
    const errors = collectErrors(page);
    await page.setViewportSize({ width: 375, height: 812 });
    await loginAsAdmin(page, baseURL);
    await page.goto('/admin/password');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#admin-password-link')).toBeVisible();
    await expect(page.locator('#admin-logout-button')).toBeVisible();
    const linkBox = await page.locator('#admin-password-link').boundingBox();
    const logoutBox = await page.locator('#admin-logout-button').boundingBox();
    // 두 버튼이 header 한 줄 안에 나란히 있다(줄바꿈으로 겹치거나 header 밖으로 밀리지 않음).
    expect(Math.abs((linkBox?.y ?? 0) - (logoutBox?.y ?? 1))).toBeLessThanOrEqual(1);
    expect((logoutBox?.x ?? 0) + (logoutBox?.width ?? 0)).toBeLessThanOrEqual(375);

    const overflowX = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflowX).toBeLessThanOrEqual(0);
    expect(errors.pageErrors).toEqual([]);
    expect(errors.consoleErrors).toEqual([]);
  });

  test('비밀번호를 변경하면 세션이 유지되고 새 비밀번호로만 로그인되며, 끝나면 원래 비밀번호로 원복된다',
    async ({ page, baseURL }) => {
      test.skip(!NEW_PASSWORD_POLICY.test(ADMIN_PASSWORD || ''),
          'ADMIN_PASSWORD가 새 비밀번호 정책을 만족하지 않아 API로 원복할 수 없으므로 성공 경로를 건너뜀');

      const errors = collectErrors(page);
      const temporary = temporaryPassword();
      let changed = false;
      try {
        await loginAsAdmin(page, baseURL);
        await page.goto('/admin/password');
        await fillPasswordForm(page, { current: ADMIN_PASSWORD, next: temporary, confirm: temporary });
        const [response] = await Promise.all([
          page.waitForResponse((res) => res.url().endsWith(PASSWORD_API)),
          page.locator('#saveButton').click(),
        ]);
        changed = response.ok();

        expect(response.status()).toBe(200);
        await expect(page.locator('#successMessage')).toBeVisible();
        await expect(page.locator('#errorMessage')).toBeHidden();
        for (const id of ['#currentPassword', '#newPassword', '#newPasswordConfirm']) {
          await expect(page.locator(id)).toHaveValue('');
        }

        // 세션 ID가 교체돼도 같은 브라우저 세션의 인증은 유지된다.
        expect((await page.context().request.get(`${baseURL}/api/admin/me`)).status()).toBe(200);
        await page.goto('/admin/dashboard');
        await expect(page).toHaveURL(/\/admin\/dashboard$/);

        expect(await loginStatus(baseURL, temporary)).toBe(200);
        expect(await loginStatus(baseURL, ADMIN_PASSWORD)).toBe(401);
        expect(errors.pageErrors).toEqual([]);
        expect(errors.consoleErrors).toEqual([]);
      } finally {
        // 변경 응답을 못 받았더라도 임시 비밀번호로 바뀌었을 수 있으므로 원래 비밀번호 로그인 가능 여부로 판단한다.
        if (changed || (await loginStatus(baseURL, ADMIN_PASSWORD)) !== 200) {
          const restored = await changePasswordViaApi(baseURL, temporary, ADMIN_PASSWORD);
          expect(restored, '관리자 비밀번호 원복 실패 - 수동 확인 필요').toBeTruthy();
        }
        expect(await loginStatus(baseURL, ADMIN_PASSWORD), '원래 비밀번호로 로그인되지 않음').toBe(200);
      }
    });
});
