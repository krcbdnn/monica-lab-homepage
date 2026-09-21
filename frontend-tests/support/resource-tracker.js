// P13-T42: Playwright E2E 테스트가 "이번 실행에서 직접 생성한" 서버 리소스만 정확한 ID로 추적하고 정리한다.
//
// 이 파일은 Playwright에 의존하지 않는 순수 로직이다(Node 내장 테스트 러너로 서버 없이 검증한다:
//   node --test src/test/js/e2e/resource-tracker.test.js).
// 실제 DELETE 호출은 deleteResource(kind, id) => Promise<{ status }>로 주입받는다.
//
// 안전 원칙: cleanup 대상은 track()으로 명시적으로 등록된 ID뿐이다.
// 제목/파일명/시각/최신 N개/ID 범위로 대상을 찾는 로직은 이 모듈에 존재하지 않는다.
'use strict';

// kind -> 정상 관리자 DELETE endpoint(뒤에 /{id}). Page는 E2E가 생성하지 않으므로 없다.
const KINDS = Object.freeze({
  pinned: '/api/admin/home-pinned-contents',
  board: '/api/admin/boards',
  program: '/api/admin/programs',
  popup: '/api/admin/popups',
  banner: '/api/admin/banners',
  menu: '/api/admin/menus',
  file: '/api/admin/files',
});

// 삭제 순서. pinned는 board/program을 가리키므로 먼저, File은 콘텐츠가 사라진 뒤 마지막에 지운다.
// 각 phase 안에서는 등록의 역순(LIFO)이라 Menu도 자식 -> 부모 순으로 지워진다(MENU_HAS_CHILDREN 409 회피).
const PHASES = Object.freeze([
  ['pinned'],
  ['board', 'program', 'popup', 'banner'],
  ['menu'],
  ['file'],
]);

class CleanupError extends Error {
  constructor(failures) {
    const lines = failures.map((f) => `  - ${f.kind} #${f.id}: ${f.reason}`);
    super(`테스트 리소스 cleanup 실패 ${failures.length}건 - 아래 리소스는 수동 정리가 필요할 수 있습니다:\n${lines.join('\n')}`);
    this.name = 'CleanupError';
    this.failures = failures;
  }
}

function assertValid(kind, id) {
  if (typeof kind !== 'string' || !Object.prototype.hasOwnProperty.call(KINDS, kind)) {
    throw new Error(`알 수 없는 리소스 종류: ${String(kind)} (허용: ${Object.keys(KINDS).join(', ')})`);
  }
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error(`${kind} ID는 양의 정수여야 합니다: ${String(id)}`);
  }
}

// 204는 성공, 404는 "이미 없음"이라 idempotent 성공이다. 그 외(401/403/409/5xx)와 네트워크 오류/timeout은 실패다.
// 자동 재시도는 하지 않는다(복잡성을 늘리지 않고 실패를 그대로 드러낸다).
async function attemptDelete(deleteResource, kind, id) {
  try {
    const response = await deleteResource(kind, id);
    const status = response && response.status;
    if (status === 204 || status === 404) {
      return null;
    }
    return `HTTP ${status}`;
  } catch (error) {
    return `error: ${error && error.message ? error.message : String(error)}`;
  }
}

function createResourceTracker({ deleteResource, onTrack } = {}) {
  if (typeof deleteResource !== 'function') {
    throw new Error('createResourceTracker: deleteResource 함수가 필요합니다');
  }
  const entries = []; // 등록 순서대로 { kind, id }

  const has = (kind, id) => entries.some((e) => e.kind === kind && e.id === id);
  const drop = (kind, id) => {
    const index = entries.findIndex((e) => e.kind === kind && e.id === id);
    if (index >= 0) {
      entries.splice(index, 1);
    }
  };

  // 생성 응답에서 얻은 정확한 ID를 등록한다. 호출부에서 감쌀 수 있도록 id를 그대로 반환한다.
  function track(kind, id) {
    assertValid(kind, id);
    if (!has(kind, id)) {
      entries.push({ kind, id });
      if (onTrack) {
        onTrack({ kind, id });
      }
    }
    return id;
  }

  // 테스트 논리상 중간 삭제가 필요한 경우에만 쓴다. 204/404면 등록에서 제거한다.
  // 실패하면 등록을 유지해서(잃어버리지 않고) 최종 cleanup()이 다시 시도할 수 있게 하고 예외를 던진다.
  async function remove(kind, id) {
    track(kind, id);
    const reason = await attemptDelete(deleteResource, kind, id);
    if (reason === null) {
      drop(kind, id);
      return;
    }
    throw new CleanupError([{ kind, id, reason }]);
  }

  // 등록된 리소스를 phase 순서/LIFO로 전부 시도한다. 하나가 실패해도 나머지는 계속 시도하고,
  // 마지막에 실패 목록을 모아 한 번에 throw한다. 성공한 것만 등록에서 제거한다.
  async function cleanup() {
    const failures = [];
    let cleaned = 0;
    for (const phase of PHASES) {
      const targets = entries.filter((e) => phase.includes(e.kind)).reverse();
      for (const { kind, id } of targets) {
        const reason = await attemptDelete(deleteResource, kind, id);
        if (reason === null) {
          drop(kind, id);
          cleaned += 1;
        } else {
          failures.push({ kind, id, reason });
        }
      }
    }
    if (failures.length > 0) {
      throw new CleanupError(failures);
    }
    return cleaned;
  }

  // 진단/검증용. kind를 주면 그 종류의 ID만 등록 순서대로 돌려준다.
  function ids(kind) {
    return entries.filter((e) => kind === undefined || e.kind === kind).map((e) => e.id);
  }

  return { track, remove, cleanup, ids };
}

module.exports = { KINDS, PHASES, CleanupError, createResourceTracker };
