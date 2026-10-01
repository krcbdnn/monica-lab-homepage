// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/home/popup-modal.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    todayLocalDateString,
    isHiddenToday,
    hideForToday,
    selectVisiblePopupIds,
    selectPopupsToShow,
    clampPosition,
    computeDefaultPosition,
    currentTopmostPopupId
} = require('../../../main/resources/static/js/home/popup-modal.js');

function fakeStorage(initial) {
    const store = Object.assign({}, initial);
    return {
        getItem: (key) => (Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null),
        setItem: (key, value) => {
            store[key] = value;
        },
        _store: store
    };
}

function throwingStorage() {
    return {
        getItem: () => {
            throw new Error('storage unavailable');
        },
        setItem: () => {
            throw new Error('storage unavailable');
        }
    };
}

test('todayLocalDateString formats a date as zero-padded YYYY-MM-DD in local time', () => {
    const date = new Date(2026, 0, 5); // 2026-01-05 (월/일 zero-pad 확인용)
    assert.equal(todayLocalDateString(date), '2026-01-05');
});

test('todayLocalDateString does not use UTC conversion (local month/day preserved)', () => {
    const date = new Date(2026, 10, 30); // 2026-11-30
    assert.equal(todayLocalDateString(date), '2026-11-30');
});

test('isHiddenToday is true when the stored value equals today\'s date string', () => {
    const storage = fakeStorage({ 'popup-hide-until:1': '2026-08-25' });
    assert.equal(isHiddenToday(storage, '1', '2026-08-25'), true);
});

test('isHiddenToday is false when the stored value is a different (older) date', () => {
    const storage = fakeStorage({ 'popup-hide-until:1': '2026-08-24' });
    assert.equal(isHiddenToday(storage, '1', '2026-08-25'), false);
});

test('isHiddenToday is false when nothing is stored for this popup id', () => {
    const storage = fakeStorage({});
    assert.equal(isHiddenToday(storage, '1', '2026-08-25'), false);
});

test('isHiddenToday fails open (returns false) when storage.getItem throws', () => {
    assert.equal(isHiddenToday(throwingStorage(), '1', '2026-08-25'), false);
});

test('hideForToday stores today\'s date string under the popup-specific key', () => {
    const storage = fakeStorage({});
    hideForToday(storage, '42', '2026-08-25');
    assert.equal(storage._store['popup-hide-until:42'], '2026-08-25');
});

test('hideForToday does not throw when storage.setItem fails', () => {
    assert.doesNotThrow(() => hideForToday(throwingStorage(), '42', '2026-08-25'));
});

test('selectVisiblePopupIds filters out ids hidden today and preserves the remaining order', () => {
    const storage = fakeStorage({
        'popup-hide-until:2': '2026-08-25',
        'popup-hide-until:3': '2026-08-24'
    });
    const result = selectVisiblePopupIds(['1', '2', '3', '4'], storage, '2026-08-25');
    assert.deepEqual(result, ['1', '3', '4']);
});

test('selectVisiblePopupIds returns all ids unchanged when none are hidden today', () => {
    const storage = fakeStorage({});
    const result = selectVisiblePopupIds(['5', '6'], storage, '2026-08-25');
    assert.deepEqual(result, ['5', '6']);
});

test('selectPopupsToShow shows the first maxVisible ids from the pool when nothing is dismissed', () => {
    const result = selectPopupsToShow(['A', 'B', 'C', 'D'], [], 3);
    assert.deepEqual(result, ['A', 'B', 'C']);
});

test('selectPopupsToShow backfills the next pool candidate after one is dismissed, keeping count at maxVisible', () => {
    // A를 닫으면 B/C는 그대로 노출 대상에 남고, 4번째 대기였던 D가 새로 포함되어 다시 3개가 된다.
    const result = selectPopupsToShow(['A', 'B', 'C', 'D'], ['A'], 3);
    assert.deepEqual(result, ['B', 'C', 'D']);
});

test('selectPopupsToShow index 0 is always the most recent among currently shown ids', () => {
    // B가 닫혀도 index 0은 여전히 pool에서 가장 앞(가장 최신)인 A다.
    const result = selectPopupsToShow(['A', 'B', 'C', 'D'], ['B'], 3);
    assert.equal(result[0], 'A');
    assert.deepEqual(result, ['A', 'C', 'D']);
});

test('selectPopupsToShow returns fewer than maxVisible once the pool is exhausted', () => {
    const result = selectPopupsToShow(['A', 'B'], ['A'], 3);
    assert.deepEqual(result, ['B']);
});

test('selectPopupsToShow returns an empty array once every candidate has been dismissed', () => {
    const result = selectPopupsToShow(['A', 'B'], ['A', 'B'], 3);
    assert.deepEqual(result, []);
});

test('clampPosition keeps a normally-sized card fully inside the viewport', () => {
    const result = clampPosition(-50, -20, 340, 400, 1440, 900);
    assert.deepEqual(result, { x: 0, y: 0 });
});

test('clampPosition prevents dragging past the right/bottom edge', () => {
    const result = clampPosition(2000, 2000, 340, 400, 1440, 900);
    assert.deepEqual(result, { x: 1440 - 340, y: 900 - 400 });
});

test('clampPosition leaves an in-range position untouched', () => {
    const result = clampPosition(100, 150, 340, 400, 1440, 900);
    assert.deepEqual(result, { x: 100, y: 150 });
});

test('clampPosition handles a card taller/wider than the viewport without producing an inverted range', () => {
    const result = clampPosition(-9999, -9999, 2000, 2000, 375, 600);
    assert.deepEqual(result, { x: 375 - 2000, y: 600 - 2000 });
    // 카드가 완전히 화면 밖으로 나가지는 않는다 - 오른쪽/아래 끝이 viewport 끝과 맞춰진다.
    assert.equal(result.x + 2000, 375);
    assert.equal(result.y + 2000, 600);
});

test('computeDefaultPosition centers rank 0 horizontally on desktop and offsets deeper ranks right/down', () => {
    const rank0 = computeDefaultPosition(0, 1440, 900, 340, 300);
    const rank1 = computeDefaultPosition(1, 1440, 900, 340, 300);
    const rank2 = computeDefaultPosition(2, 1440, 900, 340, 300);

    assert.equal(rank0.x, (1440 - 340) / 2);
    assert.equal(rank1.x, rank0.x + 40);
    assert.equal(rank2.x, rank0.x + 80);
    assert.equal(rank1.y, rank0.y + 40);
    assert.equal(rank2.y, rank0.y + 80);
});

test('computeDefaultPosition never places a card above the header clearance on desktop', () => {
    const rank0 = computeDefaultPosition(0, 1024, 768, 340, 300);
    assert.ok(rank0.y >= 96);
});

test('computeDefaultPosition applies vertical-only offset (no horizontal offset) below the mobile breakpoint', () => {
    const rank0 = computeDefaultPosition(0, 375, 812, 343, 300);
    const rank1 = computeDefaultPosition(1, 375, 812, 343, 300);

    assert.equal(rank1.x, rank0.x);
    assert.equal(rank1.y, rank0.y + 40);
});

test('computeDefaultPosition keeps mobile cards within the viewport (no horizontal overflow)', () => {
    const rank0 = computeDefaultPosition(0, 375, 812, 343, 300);
    assert.ok(rank0.x >= 0);
    assert.ok(rank0.x + 343 <= 375);
});

test('currentTopmostPopupId defaults to rank 0 (shown[0]) when nothing has been dragged', () => {
    const result = currentTopmostPopupId(['A', 'B', 'C'], {});
    assert.equal(result, 'A');
});

test('currentTopmostPopupId returns the dragged popup even if it is not rank 0', () => {
    const result = currentTopmostPopupId(['A', 'B', 'C'], { B: 101 });
    assert.equal(result, 'B');
});

test('currentTopmostPopupId picks the most recently dragged popup among several dragged ones', () => {
    const result = currentTopmostPopupId(['A', 'B', 'C'], { A: 101, C: 105 });
    assert.equal(result, 'C');
});

test('currentTopmostPopupId returns null when nothing is shown', () => {
    const result = currentTopmostPopupId([], {});
    assert.equal(result, null);
});
