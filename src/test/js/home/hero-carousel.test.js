// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/home/hero-carousel.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    AUTOPLAY_INTERVAL_MS,
    nextIndex,
    prevIndex,
    initialUserPaused,
    shouldAutoplay
} = require('../../../main/resources/static/js/home/hero-carousel.js');

test('AUTOPLAY_INTERVAL_MS is 5 seconds', () => {
    assert.equal(AUTOPLAY_INTERVAL_MS, 5000);
});

test('nextIndex wraps around from the last index back to 0', () => {
    assert.equal(nextIndex(0, 3), 1);
    assert.equal(nextIndex(1, 3), 2);
    assert.equal(nextIndex(2, 3), 0);
});

test('prevIndex wraps around from index 0 back to the last index', () => {
    assert.equal(prevIndex(0, 3), 2);
    assert.equal(prevIndex(2, 3), 1);
    assert.equal(prevIndex(1, 3), 0);
});

test('nextIndex/prevIndex return 0 when count is 0 (defensive, controls are not rendered in this case)', () => {
    assert.equal(nextIndex(0, 0), 0);
    assert.equal(prevIndex(0, 0), 0);
});

test('initialUserPaused starts paused when prefers-reduced-motion is reduce', () => {
    assert.equal(initialUserPaused(true), true);
});

test('initialUserPaused starts playing when prefers-reduced-motion is not reduce', () => {
    assert.equal(initialUserPaused(false), false);
    assert.equal(initialUserPaused(undefined), false);
});

test('shouldAutoplay is false when there is only 0 or 1 banner, regardless of other flags', () => {
    assert.equal(shouldAutoplay({ bannerCount: 0, userPaused: false, interactionPaused: false }), false);
    assert.equal(shouldAutoplay({ bannerCount: 1, userPaused: false, interactionPaused: false }), false);
});

test('shouldAutoplay is true when 2+ banners exist and nothing is pausing it', () => {
    assert.equal(shouldAutoplay({ bannerCount: 3, userPaused: false, interactionPaused: false }), true);
});

test('shouldAutoplay is false while the user has explicitly paused, even if not hovering/focused', () => {
    assert.equal(shouldAutoplay({ bannerCount: 3, userPaused: true, interactionPaused: false }), false);
});

test('shouldAutoplay is false during hover/focus interaction, even if not explicitly paused', () => {
    assert.equal(shouldAutoplay({ bannerCount: 3, userPaused: false, interactionPaused: true }), false);
});

test('reduced-motion only blocks the initial autoplay; once the user explicitly presses play, autoplay is allowed', () => {
    // 초기 상태: prefers-reduced-motion: reduce이므로 userPaused가 true로 시작 -> 자동재생 안 함
    const initialState = {
        bannerCount: 3,
        userPaused: initialUserPaused(true),
        interactionPaused: false
    };
    assert.equal(shouldAutoplay(initialState), false);

    // 사용자가 재생 버튼을 명시적으로 눌러 userPaused를 false로 전환 -> reducedMotion과 무관하게 자동재생 허용
    const afterExplicitPlay = { ...initialState, userPaused: false };
    assert.equal(shouldAutoplay(afterExplicitPlay), true);
});
