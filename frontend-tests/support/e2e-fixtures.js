// P13-T42: Playwright E2E 리소스 cleanup fixture.
//
// - tracker fixture: 테스트가 생성 직후 정확한 ID를 tracker.track(kind, id)로 등록하면, 테스트가 끝난 뒤
//   (성공/단언 실패/throw/timeout/beforeEach 실패 모두) 정상 관리자 DELETE API로 그 ID만 정리한다.
// - cleanup은 테스트의 page/context와 독립된 별도 인증 세션(request.newContext)을 쓴다. 등록된 리소스가
//   하나도 없으면 로그인조차 하지 않는다(lazy).
// - observeCreate는 UI 동작이 일으키는 실제 POST 응답을 "관찰만" 한다. 요청을 가로채거나 바꾸지 않는다
//   (route/fetch/fulfill 미사용).
// - 강제 종료(kill -9, 머신/Docker 종료 등)로 teardown이 실행되지 못한 경우는 보장 범위가 아니다.
//
// 이 파일 이름에는 .spec/.test 접미사를 쓰지 않는다(Playwright가 테스트 파일로 오인하지 않도록).
'use strict';

const crypto = require('crypto');
const base = require('@playwright/test');
const { KINDS, createResourceTracker } = require('./resource-tracker');

const DELETE_TIMEOUT_MS = 10000;
const FIXTURE_TIMEOUT_MS = 60000;

// 기존 spec의 loginAsAdmin/getXsrfToken과 같은 계약: POST /api/admin/login 후 XSRF-TOKEN 쿠키 값을
// X-XSRF-TOKEN 헤더로 보낸다. 세션은 첫 DELETE 시점에 한 번만 만들고 dispose()에서 폐기한다.
function createCleanupSession({ baseURL, loginId = process.env.ADMIN_LOGIN_ID, password = process.env.ADMIN_PASSWORD }) {
  let opened = null;

  async function open() {
    if (!loginId || !password) {
      throw new Error('cleanup 로그인 불가 - ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 필요합니다');
    }
    const context = await base.request.newContext({ baseURL, timeout: DELETE_TIMEOUT_MS });
    try {
      const login = await context.post('/api/admin/login', { data: { loginId, password } });
      if (!login.ok()) {
        throw new Error(`cleanup 로그인 실패: HTTP ${login.status()}`);
      }
      const { cookies } = await context.storageState();
      const xsrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
      if (!xsrf) {
        throw new Error('cleanup 로그인 후 XSRF-TOKEN 쿠키가 없습니다');
      }
      return { context, xsrfToken: xsrf.value };
    } catch (error) {
      await context.dispose();
      throw error;
    }
  }

  async function deleteResource(kind, id) {
    if (!opened) {
      opened = open();
    }
    const { context, xsrfToken } = await opened;
    const response = await context.delete(`${KINDS[kind]}/${id}`, { headers: { 'X-XSRF-TOKEN': xsrfToken } });
    return { status: response.status() };
  }

  async function dispose() {
    if (!opened) {
      return;
    }
    try {
      const { context } = await opened;
      await context.dispose();
    } catch (error) {
      // 로그인 실패로 열리지 못한 세션은 폐기할 것이 없다.
    }
  }

  return { deleteResource, dispose };
}

// fixture와 beforeAll/afterAll(T37)이 함께 쓰는 팩토리. 사용 후 dispose()를 반드시 호출한다.
function createTracker({ baseURL, onTrack } = {}) {
  const session = createCleanupSession({ baseURL });
  const tracker = createResourceTracker({ deleteResource: session.deleteResource, onTrack });
  return { tracker, dispose: session.dispose };
}

// UI 동작(클릭/저장/업로드)이 일으키는 "해당 kind의 생성 POST" 응답을 관찰해서 exact ID를 tracker에 등록한다.
// 반드시 UI 동작보다 먼저 호출하고, 필요한 시점에 반환된 promise를 await한다:
//   const idP = observeCreate(page, tracker, 'board');
//   await ...클릭/저장...;
//   const id = await idP;
// 응답이 도착한 즉시 등록하므로 이후 waitForURL/단언이 실패해도 이미 cleanup 대상이다.
// method/pathname/2xx/JSON/data.id(양의 정수)가 하나라도 맞지 않으면 등록하지 않고 예외를 던진다.
function observeCreate(page, tracker, kind, { timeout = 15000 } = {}) {
  const endpoint = KINDS[kind];
  if (!endpoint) {
    throw new Error(`observeCreate: 알 수 없는 리소스 종류: ${String(kind)}`);
  }
  const promise = page
    .waitForResponse(
      (response) => response.request().method() === 'POST' && new URL(response.url()).pathname === endpoint,
      { timeout }
    )
    .then(async (response) => {
      const where = `${kind} 생성 POST ${endpoint} (HTTP ${response.status()})`;
      if (!response.ok()) {
        throw new Error(`observeCreate: ${where} - 성공(2xx) 응답이 아닙니다`);
      }
      let body;
      try {
        body = await response.json();
      } catch (error) {
        throw new Error(`observeCreate: ${where} - 응답 JSON 파싱 실패: ${error.message}`);
      }
      const id = body && body.data && body.data.id;
      if (!Number.isInteger(id) || id <= 0) {
        throw new Error(`observeCreate: ${where} - 응답 data.id가 양의 정수가 아닙니다`);
      }
      return tracker.track(kind, id);
    });
  // 테스트가 먼저 실패해 아무도 await하지 않는 경우의 unhandled rejection을 막는다(await하면 그대로 throw된다).
  promise.catch(() => {});
  return promise;
}

// ---------------------------------------------------------------------------------------------------------------
// observeNavigatingCreate: "폼 submit -> fetch POST 성공 -> location.href 이동" 구조의 UI 생성용.
//
// 이 구조에서는 이동하는 순간 이전 document의 응답 body가 폐기돼 page.waitForResponse/page.on('response')로는
// body를 읽을 수 없다(Phase 0에서 재현). 그래서 이동 전에 "페이지 안에서" 응답의 clone에서 id를 읽어 Node로 전달한다.
//   - 요청/응답을 가로채거나 바꾸지 않는다(route/fetch/fulfill 미사용). 원래 fetch가 원래 인자로 정확히 한 번 실행되고,
//     호출자에게는 원래 fetch가 돌려준 promise가 그대로 반환된다. 응답은 clone()으로만 읽는다.
//   - id 전달은 이 page에 등록하는 exposeFunction이다. 이름에 호출마다 만든 무작위 token을 넣어 다른 테스트/worker/
//     호출과 겹치지 않는다(localStorage나 고정 key, 서버 측 공유 상태를 쓰지 않는다).
//   - Node 쪽 콜백은 응답 도착 즉시 tracker.track()을 호출하므로 이동 완료 여부와 무관하게 이미 cleanup 대상이다.
//   - 이동을 동반하는 Board/Popup UI 생성(T23/T29/Popup CKEditor)에서만 쓴다. 이동이 없는 업로드는 observeCreate를 쓴다.
// ---------------------------------------------------------------------------------------------------------------
const NAVIGATING_KINDS = ['board', 'popup'];

// 브라우저 안에서 실행된다. page.evaluate로 직렬화되므로 이 함수 밖의 변수를 참조하면 안 된다.
function installCreateObserver({ endpoint, reportName }) {
  const markerKey = reportName + '_installed';
  if (window[markerKey]) {
    return; // 같은 document에 같은 관찰자를 두 번 설치하지 않는다
  }
  window[markerKey] = true;
  const originalFetch = window.fetch;
  window.fetch = function (...args) {
    const promise = originalFetch.apply(this, args); // 인자/this를 바꾸지 않고 정확히 한 번 호출
    try {
      const input = args[0];
      const init = args[1];
      const requestLike = input !== null && typeof input === 'object' && typeof input.url === 'string' ? input : null;
      const method = String((init && init.method) || (requestLike && requestLike.method) || 'GET').toUpperCase();
      const url = requestLike ? requestLike.url : String(input);
      if (method === 'POST' && new URL(url, window.location.href).pathname === endpoint) {
        promise
          .then((response) => {
            const status = response.status;
            if (!response.ok) {
              window[reportName]({ status, error: '성공(2xx) 응답이 아닙니다' });
              return undefined;
            }
            // clone만 읽는다. 원본 response는 애플리케이션 코드가 그대로 소비한다.
            return response
              .clone()
              .json()
              .then(
                (body) => window[reportName]({ status, id: body && body.data ? body.data.id : undefined }),
                (error) => window[reportName]({ status, error: 'JSON 파싱 실패: ' + error.message })
              );
          })
          .catch(() => {});
      }
    } catch (error) {
      // 관찰 실패가 애플리케이션 동작에 영향을 주면 안 된다.
    }
    return promise; // 호출자에게는 원래 fetch의 promise를 그대로 돌려준다
  };
}

// 반드시 submit(저장) 클릭 "전에" 같은 document에서 await로 호출한다. 반환된 { id } promise는 필요한 시점에 await한다:
//   const created = await observeNavigatingCreate(page, tracker, 'board', { timeout: 10000 });
//   await Promise.all([page.waitForURL(...), page.locator('button[type="submit"]').click()]);
//   const boardId = await created.id;
// 실패(POST 미발생/non-2xx/JSON 오류/id 없음/timeout)는 최신 목록 조회 같은 fallback 없이 그대로 예외가 된다.
async function observeNavigatingCreate(page, tracker, kind, { timeout = 15000 } = {}) {
  if (!NAVIGATING_KINDS.includes(kind)) {
    throw new Error(`observeNavigatingCreate: 지원하지 않는 종류: ${String(kind)} (허용: ${NAVIGATING_KINDS.join(', ')})`);
  }
  const endpoint = KINDS[kind];
  const reportName = `__pw_create_${kind}_${crypto.randomBytes(6).toString('hex')}`;

  let settled = false;
  let resolveId;
  let rejectId;
  const id = new Promise((resolve, reject) => {
    resolveId = resolve;
    rejectId = reject;
  });
  id.catch(() => {}); // 아무도 await하지 않고 테스트가 먼저 끝나는 경우의 unhandled rejection 방지

  const fail = (reason) => {
    if (!settled) {
      settled = true;
      clearTimeout(timer);
      rejectId(new Error(`observeNavigatingCreate: ${kind} 생성 POST ${endpoint} - ${reason}`));
    }
  };
  const timer = setTimeout(() => fail(`${timeout}ms 안에 생성 응답을 관찰하지 못했습니다`), timeout);
  timer.unref();

  await page.exposeFunction(reportName, (payload) => {
    if (!payload || payload.error) {
      fail(`(HTTP ${payload && payload.status}) ${payload && payload.error ? payload.error : '응답 정보 없음'}`);
      return;
    }
    const createdId = payload.id;
    if (!Number.isInteger(createdId) || createdId <= 0) {
      fail(`(HTTP ${payload.status}) 응답 data.id가 양의 정수가 아닙니다`);
      return;
    }
    tracker.track(kind, createdId); // 이동 전 응답 도착 시점에 즉시 등록. 중복 POST가 있어도 모두 추적한다.
    if (!settled) {
      settled = true;
      clearTimeout(timer);
      resolveId(createdId);
    }
  });
  await page.evaluate(installCreateObserver, { endpoint, reportName });
  return { id };
}

const test = base.test.extend({
  tracker: [
    async ({ baseURL }, use, testInfo) => {
      const { tracker, dispose } = createTracker({
        baseURL,
        // 진단용 annotation. cleanup 성공 여부는 이 값이 아니라 tracker 자체가 책임진다.
        onTrack: ({ kind, id }) => testInfo.annotations.push({ type: 'tracked-resource', description: `${kind}#${id}` }),
      });
      await use(tracker);
      try {
        await tracker.cleanup();
      } finally {
        await dispose();
      }
    },
    { timeout: FIXTURE_TIMEOUT_MS },
  ],
});

module.exports = { test, expect: base.expect, createTracker, observeCreate, observeNavigatingCreate, installCreateObserver };
