// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/e2e/resource-tracker.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    KINDS,
    PHASES,
    CleanupError,
    createResourceTracker
} = require('../../../../frontend-tests/support/resource-tracker');
const { installCreateObserver } = require('../../../../frontend-tests/support/e2e-fixtures');

// 호출 기록을 남기고, 지정한 (kind#id -> status | Error) 응답을 돌려주는 가짜 deleteResource.
function fakeDeleter(responses = {}) {
    const calls = [];
    const deleteResource = async (kind, id) => {
        calls.push(`${kind}#${id}`);
        const outcome = responses[`${kind}#${id}`];
        if (outcome instanceof Error) {
            throw outcome;
        }
        return { status: outcome === undefined ? 204 : outcome };
    };
    return { calls, deleteResource };
}

test('KINDS는 정확히 필요한 7개 종류와 정상 관리자 endpoint만 가진다', () => {
    assert.deepEqual(Object.keys(KINDS).sort(), ['banner', 'board', 'file', 'menu', 'pinned', 'popup', 'program']);
    assert.equal(KINDS.board, '/api/admin/boards');
    assert.equal(KINDS.pinned, '/api/admin/home-pinned-contents');
    assert.equal(KINDS.file, '/api/admin/files');
});

test('deleteResource 없이는 생성할 수 없다', () => {
    assert.throws(() => createResourceTracker(), /deleteResource/);
});

test('track: 유효한 등록은 id를 그대로 반환하고 ids()에 등록 순서대로 남는다', () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter().deleteResource });
    assert.equal(tracker.track('board', 10), 10);
    assert.equal(tracker.track('file', 3), 3);
    assert.equal(tracker.track('board', 11), 11);
    assert.deepEqual(tracker.ids('board'), [10, 11]);
    assert.deepEqual(tracker.ids('file'), [3]);
    assert.deepEqual(tracker.ids(), [10, 3, 11]);
});

test('track: 알 수 없는 kind는 거부한다', () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter().deleteResource });
    assert.throws(() => tracker.track('page', 1), /알 수 없는 리소스 종류/);
    assert.throws(() => tracker.track('boards', 1), /알 수 없는 리소스 종류/);
    assert.throws(() => tracker.track(undefined, 1), /알 수 없는 리소스 종류/);
    assert.throws(() => tracker.track('toString', 1), /알 수 없는 리소스 종류/);
    assert.deepEqual(tracker.ids(), []);
});

test('track: 양의 정수가 아닌 id는 거부한다', () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter().deleteResource });
    for (const bad of [0, -1, 1.5, '12', null, undefined, NaN, Infinity]) {
        assert.throws(() => tracker.track('board', bad), /양의 정수/, `id=${String(bad)}`);
    }
    assert.deepEqual(tracker.ids(), []);
});

test('track: 중복 등록은 한 번만 남고 onTrack도 한 번만 호출된다', () => {
    const seen = [];
    const tracker = createResourceTracker({
        deleteResource: fakeDeleter().deleteResource,
        onTrack: (entry) => seen.push(entry)
    });
    assert.equal(tracker.track('board', 5), 5);
    assert.equal(tracker.track('board', 5), 5);
    tracker.track('program', 5);
    assert.deepEqual(tracker.ids(), [5, 5]);
    assert.deepEqual(seen, [{ kind: 'board', id: 5 }, { kind: 'program', id: 5 }]);
});

test('cleanup: phase 순서는 pinned -> board/program/popup/banner -> menu -> file 이다', async () => {
    assert.deepEqual(PHASES.map((phase) => [...phase]), [
        ['pinned'], ['board', 'program', 'popup', 'banner'], ['menu'], ['file']
    ]);
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    // 일부러 뒤섞인 순서로 등록한다.
    tracker.track('file', 1);
    tracker.track('menu', 2);
    tracker.track('banner', 3);
    tracker.track('pinned', 4);
    tracker.track('popup', 5);
    tracker.track('program', 6);
    tracker.track('board', 7);
    await tracker.cleanup();
    // pinned 먼저, 콘텐츠 4종(전체 등록의 역순), menu, file 마지막.
    assert.deepEqual(calls, ['pinned#4', 'board#7', 'program#6', 'popup#5', 'banner#3', 'menu#2', 'file#1']);
});

test('cleanup: 같은 종류 안에서는 등록의 역순(LIFO)이다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    [11, 12, 13].forEach((id) => tracker.track('board', id));
    await tracker.cleanup();
    assert.deepEqual(calls, ['board#13', 'board#12', 'board#11']);
});

test('cleanup: Menu는 LIFO라 자식(나중 등록)이 부모보다 먼저 삭제된다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('menu', 100); // 부모
    tracker.track('menu', 101); // 자식
    await tracker.cleanup();
    assert.deepEqual(calls, ['menu#101', 'menu#100']);
});

test('cleanup: File은 항상 콘텐츠보다 나중이다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('file', 1);
    tracker.track('board', 2);
    tracker.track('file', 3);
    await tracker.cleanup();
    assert.deepEqual(calls, ['board#2', 'file#3', 'file#1']);
});

test('cleanup: 204는 성공이고 성공한 항목은 등록에서 제거된다', async () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter().deleteResource });
    tracker.track('board', 1);
    assert.equal(await tracker.cleanup(), 1);
    assert.deepEqual(tracker.ids(), []);
});

test('cleanup: 404는 idempotent 성공이다(이미 없는 리소스)', async () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter({ 'board#1': 404 }).deleteResource });
    tracker.track('board', 1);
    assert.equal(await tracker.cleanup(), 1);
    assert.deepEqual(tracker.ids(), []);
});

for (const status of [401, 403, 409, 500, 502, 200]) {
    test(`cleanup: HTTP ${status}는 실패이고 kind/id/status가 오류에 담긴다`, async () => {
        const tracker = createResourceTracker({ deleteResource: fakeDeleter({ 'board#7': status }).deleteResource });
        tracker.track('board', 7);
        await assert.rejects(tracker.cleanup(), (error) => {
            assert.ok(error instanceof CleanupError);
            assert.match(error.message, new RegExp(`board #7: HTTP ${status}`));
            assert.deepEqual(error.failures, [{ kind: 'board', id: 7, reason: `HTTP ${status}` }]);
            return true;
        });
        assert.deepEqual(tracker.ids(), [7], '실패한 항목은 등록에 남는다');
    });
}

test('cleanup: 네트워크 오류/timeout 예외도 실패로 보고된다', async () => {
    const { deleteResource } = fakeDeleter({
        'board#1': new Error('connect ECONNREFUSED'),
        'file#2': new Error('Timeout 10000ms exceeded')
    });
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('board', 1);
    tracker.track('file', 2);
    await assert.rejects(tracker.cleanup(), (error) => {
        assert.ok(error instanceof CleanupError);
        assert.match(error.message, /board #1: error: connect ECONNREFUSED/);
        assert.match(error.message, /file #2: error: Timeout 10000ms exceeded/);
        return true;
    });
});

test('cleanup: 하나가 실패해도 나머지를 계속 시도하고 실패만 모아서 보고한다', async () => {
    const { calls, deleteResource } = fakeDeleter({ 'board#2': 500, 'menu#9': 409 });
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('board', 1);
    tracker.track('board', 2);
    tracker.track('board', 3);
    tracker.track('menu', 9);
    tracker.track('file', 4);
    await assert.rejects(tracker.cleanup(), (error) => {
        assert.deepEqual(error.failures.map((f) => `${f.kind}#${f.id}:${f.reason}`), ['board#2:HTTP 500', 'menu#9:HTTP 409']);
        return true;
    });
    assert.deepEqual(calls, ['board#3', 'board#2', 'board#1', 'menu#9', 'file#4'], '실패 이후에도 File까지 시도한다');
    assert.deepEqual(tracker.ids(), [2, 9], '성공한 것만 제거되고 실패한 것은 남는다');
});

test('cleanup: 실패 후 다시 호출하면 남은 항목만 재시도한다(자동 재시도는 없다)', async () => {
    const responses = { 'board#1': 500 };
    const { calls, deleteResource } = fakeDeleter(responses);
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('board', 1);
    await assert.rejects(tracker.cleanup(), CleanupError);
    assert.deepEqual(calls, ['board#1'], '한 번의 cleanup에서는 재시도하지 않는다');
    responses['board#1'] = 204;
    await tracker.cleanup();
    assert.deepEqual(calls, ['board#1', 'board#1']);
    assert.deepEqual(tracker.ids(), []);
});

test('cleanup: 등록이 없으면 deleteResource를 호출하지 않는다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    assert.equal(await tracker.cleanup(), 0);
    assert.deepEqual(calls, []);
});

test('remove: 204면 등록에서 제거되어 이후 cleanup이 다시 삭제하지 않는다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    tracker.track('popup', 8);
    await tracker.remove('popup', 8);
    assert.deepEqual(tracker.ids(), []);
    await tracker.cleanup();
    assert.deepEqual(calls, ['popup#8'], '이중 DELETE가 없다');
});

test('remove: 404도 이미 없는 것으로 보고 등록에서 제거한다', async () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter({ 'popup#8': 404 }).deleteResource });
    tracker.track('popup', 8);
    await tracker.remove('popup', 8);
    assert.deepEqual(tracker.ids(), []);
});

for (const status of [401, 403, 409, 500]) {
    test(`remove: HTTP ${status}이면 예외를 던지고 등록을 유지한다(최종 cleanup이 다시 시도)`, async () => {
        const responses = { 'popup#8': status };
        const { calls, deleteResource } = fakeDeleter(responses);
        const tracker = createResourceTracker({ deleteResource });
        tracker.track('popup', 8);
        await assert.rejects(tracker.remove('popup', 8), (error) => {
            assert.ok(error instanceof CleanupError);
            assert.match(error.message, new RegExp(`popup #8: HTTP ${status}`));
            return true;
        });
        assert.deepEqual(tracker.ids('popup'), [8]);
        responses['popup#8'] = 204;
        await tracker.cleanup();
        assert.deepEqual(calls, ['popup#8', 'popup#8']);
        assert.deepEqual(tracker.ids(), []);
    });
}

test('remove: 네트워크 오류/timeout이면 예외를 던지고 등록을 유지한다', async () => {
    const tracker = createResourceTracker({
        deleteResource: fakeDeleter({ 'popup#8': new Error('Timeout 10000ms exceeded') }).deleteResource
    });
    tracker.track('popup', 8);
    await assert.rejects(tracker.remove('popup', 8), /popup #8: error: Timeout 10000ms exceeded/);
    assert.deepEqual(tracker.ids('popup'), [8]);
});

test('remove: 등록되지 않은 유효한 리소스라도 실패하면 등록해서 잃어버리지 않는다', async () => {
    const tracker = createResourceTracker({ deleteResource: fakeDeleter({ 'board#4': 500 }).deleteResource });
    await assert.rejects(tracker.remove('board', 4), CleanupError);
    assert.deepEqual(tracker.ids('board'), [4]);
});

test('remove: 잘못된 kind/id는 DELETE 호출 없이 거부한다', async () => {
    const { calls, deleteResource } = fakeDeleter();
    const tracker = createResourceTracker({ deleteResource });
    await assert.rejects(tracker.remove('page', 1), /알 수 없는 리소스 종류/);
    await assert.rejects(tracker.remove('board', 0), /양의 정수/);
    assert.deepEqual(calls, []);
});

// ---------------------------------------------------------------------------------------------------------------
// installCreateObserver(ALT2의 브라우저 측 fetch 관찰자): 가짜 window로 fetch semantics 보존을 검증한다.
// (실제 브라우저 동작 자체는 Playwright E2E가 검증한다.)
// ---------------------------------------------------------------------------------------------------------------
const ENDPOINT = '/api/admin/boards';
const REPORT = '__pw_create_board_test';

function fakeResponse({ status = 201, body, jsonThrows = false } = {}) {
    const cloneObject = {
        json: async () => {
            if (jsonThrows) {
                throw new Error('Unexpected token <');
            }
            return body;
        }
    };
    const response = {
        ok: status >= 200 && status < 300,
        status,
        cloneCalls: 0,
        bodyConsumed: false,
        clone() {
            response.cloneCalls += 1;
            return cloneObject;
        },
        json: async () => {
            response.bodyConsumed = true;
            return body;
        }
    };
    return response;
}

// window.fetch를 기록용 fake로 설치하고 관찰자를 붙인다. 반환: { win, fetchCalls, reports }.
function setupBrowser(fetchImpl) {
    const fetchCalls = [];
    const reports = [];
    const win = {
        location: { href: 'http://localhost:8088/admin/boards/new' },
        [REPORT]: (payload) => reports.push(payload)
    };
    win.fetch = function (...args) {
        fetchCalls.push({ self: this, args });
        return fetchImpl(...args);
    };
    globalThis.window = win;
    installCreateObserver({ endpoint: ENDPOINT, reportName: REPORT });
    return { win, fetchCalls, reports };
}
const settle = () => new Promise((resolve) => setImmediate(resolve));
test.afterEach(() => {
    delete globalThis.window;
});

test('fetch 관찰자: 원래 fetch를 같은 인자/this로 정확히 한 번 호출하고 같은 promise를 그대로 돌려준다', async () => {
    const response = fakeResponse({ body: { data: { id: 42 } } });
    const original = Promise.resolve(response);
    const { win, fetchCalls } = setupBrowser(() => original);
    const init = { method: 'POST', body: '{"a":1}', headers: { 'X-Test': '1' } };
    const returned = win.fetch('/api/admin/boards', init);
    assert.equal(returned, original, '호출자는 원래 fetch가 돌려준 바로 그 promise를 받는다');
    assert.equal(await returned, response, '원본 response 객체가 그대로 전달된다');
    await settle();
    assert.equal(fetchCalls.length, 1, '동일 요청을 중복 전송하지 않는다');
    assert.equal(fetchCalls[0].self, win);
    assert.equal(fetchCalls[0].args[0], '/api/admin/boards');
    assert.equal(fetchCalls[0].args[1], init, 'init 객체를 복사/변경하지 않는다');
    assert.deepEqual(init, { method: 'POST', body: '{"a":1}', headers: { 'X-Test': '1' } });
});

test('fetch 관찰자: 생성 POST 성공 응답의 clone에서 status/id를 보고하고 원본 body는 소비하지 않는다', async () => {
    const response = fakeResponse({ status: 201, body: { success: true, data: { id: 42 } } });
    const { win, reports } = setupBrowser(async () => response);
    await win.fetch('/api/admin/boards', { method: 'POST' });
    await settle();
    assert.deepEqual(reports, [{ status: 201, id: 42 }]);
    assert.equal(response.cloneCalls, 1);
    assert.equal(response.bodyConsumed, false, '원본 response body는 애플리케이션이 읽을 수 있게 남아 있다');
});

test('fetch 관찰자: query string이 있어도, 절대 URL이어도 pathname 기준으로 판별한다', async () => {
    const { win, reports } = setupBrowser(async () => fakeResponse({ body: { data: { id: 7 } } }));
    await win.fetch('/api/admin/boards?draft=1', { method: 'POST' });
    await win.fetch('http://localhost:8088/api/admin/boards', { method: 'post' });
    await settle();
    assert.deepEqual(reports, [{ status: 201, id: 7 }, { status: 201, id: 7 }]);
});

test('fetch 관찰자: Request 객체 입력도 처리하고, init.method가 있으면 우선한다', async () => {
    const { win, reports } = setupBrowser(async () => fakeResponse({ body: { data: { id: 9 } } }));
    await win.fetch({ url: 'http://localhost:8088/api/admin/boards', method: 'POST' });
    await win.fetch({ url: '/api/admin/boards', method: 'GET' }, { method: 'POST' });
    await settle();
    assert.equal(reports.length, 2);
    assert.deepEqual(reports[0], { status: 201, id: 9 });
});

test('fetch 관찰자: 대상이 아닌 요청(GET/PUT/DELETE/다른 pathname)은 읽지도 보고하지도 않는다', async () => {
    const response = fakeResponse({ body: { data: { id: 1 } } });
    const { win, reports } = setupBrowser(async () => response);
    await win.fetch('/api/admin/boards');
    await win.fetch('/api/admin/boards', { method: 'PUT' });
    await win.fetch('/api/admin/boards/5', { method: 'DELETE' });
    await win.fetch('/api/admin/boards/5', { method: 'POST' });
    await win.fetch('/api/admin/popups', { method: 'POST' });
    await win.fetch('/api/admin/files', { method: 'POST' });
    await settle();
    assert.deepEqual(reports, []);
    assert.equal(response.cloneCalls, 0, '대상이 아닌 응답은 clone도 하지 않는다');
});

test('fetch 관찰자: 2xx가 아니면 error를 보고하고, 이때도 응답 body는 읽지 않는다', async () => {
    const response = fakeResponse({ status: 400, body: { data: null } });
    const { win, reports } = setupBrowser(async () => response);
    const result = await win.fetch('/api/admin/boards', { method: 'POST' });
    await settle();
    assert.equal(result, response);
    assert.deepEqual(reports, [{ status: 400, error: '성공(2xx) 응답이 아닙니다' }]);
    assert.equal(response.cloneCalls, 0);
});

test('fetch 관찰자: JSON 파싱에 실패하면 error를 보고하고 원본 응답은 그대로 반환한다', async () => {
    const response = fakeResponse({ jsonThrows: true });
    const { win, reports } = setupBrowser(async () => response);
    assert.equal(await win.fetch('/api/admin/boards', { method: 'POST' }), response);
    await settle();
    assert.equal(reports.length, 1);
    assert.equal(reports[0].status, 201);
    assert.match(reports[0].error, /JSON 파싱 실패/);
});

test('fetch 관찰자: data.id가 없으면 id 없이 보고한다(임의 ID를 만들지 않는다)', async () => {
    const { win, reports } = setupBrowser(async () => fakeResponse({ body: { success: true } }));
    await win.fetch('/api/admin/boards', { method: 'POST' });
    await settle();
    assert.deepEqual(reports, [{ status: 201, id: undefined }]);
});

test('fetch 관찰자: 원래 fetch가 reject되면 호출자에게 그 reject가 그대로 전달되고 보고는 없다', async () => {
    const failure = new Error('network down');
    const { win, reports } = setupBrowser(() => Promise.reject(failure));
    await assert.rejects(win.fetch('/api/admin/boards', { method: 'POST' }), (error) => error === failure);
    await settle();
    assert.deepEqual(reports, []);
});

test('fetch 관찰자: 보고 함수가 예외를 던져도 호출자가 받는 응답에는 영향이 없다', async () => {
    const response = fakeResponse({ body: { data: { id: 3 } } });
    const { win } = setupBrowser(async () => response);
    win[REPORT] = () => {
        throw new Error('binding gone');
    };
    assert.equal(await win.fetch('/api/admin/boards', { method: 'POST' }), response);
    await settle();
});

test('fetch 관찰자: 같은 document에 같은 관찰자를 두 번 설치해도 한 번만 감싼다', async () => {
    const { win, fetchCalls, reports } = setupBrowser(async () => fakeResponse({ body: { data: { id: 5 } } }));
    installCreateObserver({ endpoint: ENDPOINT, reportName: REPORT });
    await win.fetch('/api/admin/boards', { method: 'POST' });
    await settle();
    assert.equal(fetchCalls.length, 1);
    assert.equal(reports.length, 1);
});
