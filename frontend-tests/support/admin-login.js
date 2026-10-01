// P15-T3: 관리자 로그인 공통 helper.
//
// 운영 Nginx는 POST /api/admin/login에 클라이언트 IP 기준 rate limit(5r/m, burst=5, nodelay, 429)을 건다.
// 로컬 Docker stack(HTTPS base URL)으로 E2E 전체를 돌리면 spec 전체의 로그인 횟수가 이 한도를 넘으므로,
// 운영 계약값을 완화하지 않고 테스트 쪽에서 429일 때만 bucket이 1건 회복될 때까지(12초) 기다렸다가 재시도한다.
// - 429 외의 응답(200/401/400 등)은 그대로 돌려준다. 기존 spec의 성공/실패 판정은 바뀌지 않는다.
// - 기다린 만큼 현재 테스트/hook의 timeout을 늘린다(대기 때문에 원래 테스트 시간 예산이 줄지 않도록).
// - rate limit 자체를 검증하는 admin-login-rate-limit.spec.js는 이 helper를 쓰지 않는다(429를 그대로 봐야 한다).
//
// 이 파일 이름에는 .spec/.test 접미사를 쓰지 않는다(Playwright가 테스트 파일로 오인하지 않도록).
'use strict';

const base = require('@playwright/test');

const LOGIN_API = '/api/admin/login';
const RATE_LIMIT_RETRY_WAIT_MS = 13000; // 5r/m = 12초당 1건 회복 + 여유 1초
const RATE_LIMIT_MAX_WAIT_MS = 10 * 60 * 1000;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// 테스트/hook/fixture 밖에서 호출되면 test.info()가 throw하므로 timeout 연장 없이 기다리기만 한다.
function extendCurrentTimeout(ms) {
  try {
    const info = base.test.info();
    if (info.timeout > 0) {
      info.setTimeout(info.timeout + ms);
    }
  } catch (error) {
    // 테스트 컨텍스트 밖(독립 스크립트 등)
  }
}

function isRateLimited(status) {
  return status === 429;
}

// APIRequestContext(context.request / request.newContext())로 로그인 POST를 보내고, 429면 대기 후 재시도한다.
// url은 호출부 기존 형태(`${baseURL}/api/admin/login` 또는 baseURL이 설정된 context의 상대 경로)를 그대로 받는다.
async function postAdminLogin(request, data, { url = LOGIN_API } = {}) {
  let waited = 0;
  for (;;) {
    const response = await request.post(url, { data });
    if (!isRateLimited(response.status()) || waited >= RATE_LIMIT_MAX_WAIT_MS) {
      return response;
    }
    extendCurrentTimeout(RATE_LIMIT_RETRY_WAIT_MS);
    await sleep(RATE_LIMIT_RETRY_WAIT_MS);
    waited += RATE_LIMIT_RETRY_WAIT_MS;
  }
}

// 실제 로그인 화면으로 로그인한다. 로그인 화면은 429면 이동하지 않고 안내 문구만 표시하므로,
// 로그인 API 응답이 429일 때만 대기 후 같은 폼을 다시 제출하고, 그 외에는 화면 이동을 기다린다.
async function loginViaAdminLoginPage(page, { loginId, password }) {
  await page.goto('/admin/login');
  await page.fill('input[name=loginId]', loginId);
  await page.fill('input[name=password]', password);
  let waited = 0;
  for (;;) {
    const responsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' && new URL(response.url()).pathname === LOGIN_API);
    await page.click('button[type=submit]');
    const response = await responsePromise;
    if (!isRateLimited(response.status()) || waited >= RATE_LIMIT_MAX_WAIT_MS) {
      break;
    }
    extendCurrentTimeout(RATE_LIMIT_RETRY_WAIT_MS);
    await page.waitForTimeout(RATE_LIMIT_RETRY_WAIT_MS);
    waited += RATE_LIMIT_RETRY_WAIT_MS;
  }
  await page.waitForURL((url) => url.pathname !== '/admin/login');
}

module.exports = { postAdminLogin, loginViaAdminLoginPage };
