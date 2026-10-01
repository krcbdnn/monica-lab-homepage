(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.HeroCarousel = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var AUTOPLAY_INTERVAL_MS = 5000;

    function nextIndex(currentIndex, count) {
        if (count <= 0) {
            return 0;
        }
        return (currentIndex + 1) % count;
    }

    function prevIndex(currentIndex, count) {
        if (count <= 0) {
            return 0;
        }
        return (currentIndex - 1 + count) % count;
    }

    // prefers-reduced-motion: reduce인 환경에서는 초기 자동재생을 시작하지 않는다.
    // 이후 사용자가 재생 버튼을 명시적으로 누르면(userPaused=false로 전환) reducedMotion과
    // 무관하게 자동재생이 허용되므로, reducedMotion은 초기 userPaused 값에만 반영한다.
    function initialUserPaused(prefersReducedMotion) {
        return prefersReducedMotion === true;
    }

    // 자동재생 가능 여부를 결정하는 순수 함수. userPaused(사용자의 명시적 일시정지)가
    // interactionPaused(hover/focus로 인한 임시 정지)보다 우선하지 않고 동등하게 "정지 사유"로
    // 취급되지만, interactionPaused는 mouseleave/focusout 시 자동 해제되는 반면 userPaused는
    // 재생 버튼을 다시 눌러야만 해제된다는 점이 상태 전이(wiring) 쪽 책임으로 분리되어 있다.
    function shouldAutoplay(state) {
        return state.bannerCount > 1 && !state.userPaused && !state.interactionPaused;
    }

    function bootstrap(doc, win) {
        var viewport = doc.getElementById('hero-viewport');
        var controls = doc.getElementById('hero-controls');
        if (!viewport || !controls) {
            return;
        }

        var slides = Array.prototype.slice.call(viewport.querySelectorAll('.hero__slide'));
        var indicators = Array.prototype.slice.call(controls.querySelectorAll('.hero__indicator'));
        var prevButton = doc.getElementById('hero-prev');
        var nextButton = doc.getElementById('hero-next');
        var playPauseButton = doc.getElementById('hero-play-pause');
        if (slides.length < 2 || !prevButton || !nextButton || !playPauseButton) {
            return;
        }

        var reducedMotionQuery = win.matchMedia ? win.matchMedia('(prefers-reduced-motion: reduce)') : null;
        var currentIndex = 0;
        var timerId = null;
        var state = {
            bannerCount: slides.length,
            userPaused: initialUserPaused(reducedMotionQuery ? reducedMotionQuery.matches : false),
            interactionPaused: false
        };

        function applyActiveSlide() {
            slides.forEach(function (slide, index) {
                slide.hidden = index !== currentIndex;
            });
            indicators.forEach(function (indicator, index) {
                var isActive = index === currentIndex;
                indicator.classList.toggle('is-active', isActive);
                if (isActive) {
                    indicator.setAttribute('aria-current', 'true');
                } else {
                    indicator.removeAttribute('aria-current');
                }
            });
        }

        // 이 버튼은 "다음 클릭에 일어날 동작"을 라벨로 보여주는 재생/일시정지 토글(미디어 플레이어와 동일 관용구)이라
        // aria-pressed(고정된 라벨을 가진 on/off 토글용, 예: Bold 버튼)를 쓰면 라벨과 상태가 모순되게 읽힌다
        // (예: userPaused=true인데 라벨이 "재생"이면 "재생, 눌림"으로 읽혀 "자동 전환 중"처럼 들린다).
        // WAI-ARIA APG Carousel 패턴도 이 경우 aria-pressed 없이 접근 가능한 이름 변경만으로 상태를 전달한다.
        function syncPlayPauseButton() {
            playPauseButton.textContent = state.userPaused ? '재생' : '일시정지';
            playPauseButton.setAttribute('aria-label', state.userPaused ? '배너 자동 전환 재생' : '배너 자동 전환 일시정지');
        }

        function stopTimer() {
            if (timerId !== null) {
                win.clearInterval(timerId);
                timerId = null;
            }
        }

        function restartTimerIfNeeded() {
            stopTimer();
            if (!shouldAutoplay(state)) {
                return;
            }
            timerId = win.setInterval(function () {
                goTo(nextIndex(currentIndex, slides.length));
            }, AUTOPLAY_INTERVAL_MS);
        }

        function goTo(index) {
            currentIndex = index;
            applyActiveSlide();
        }

        function handlePrev() {
            goTo(prevIndex(currentIndex, slides.length));
            restartTimerIfNeeded();
        }

        function handleNext() {
            goTo(nextIndex(currentIndex, slides.length));
            restartTimerIfNeeded();
        }

        function handlePlayPauseClick() {
            state.userPaused = !state.userPaused;
            syncPlayPauseButton();
            restartTimerIfNeeded();
        }

        function handleInteractionEnter() {
            state.interactionPaused = true;
            restartTimerIfNeeded();
        }

        function handleInteractionLeave() {
            state.interactionPaused = false;
            restartTimerIfNeeded();
        }

        prevButton.addEventListener('click', handlePrev);
        nextButton.addEventListener('click', handleNext);
        playPauseButton.addEventListener('click', handlePlayPauseClick);

        indicators.forEach(function (indicator, index) {
            indicator.addEventListener('click', function () {
                goTo(index);
                restartTimerIfNeeded();
            });
        });

        viewport.addEventListener('mouseenter', handleInteractionEnter);
        viewport.addEventListener('mouseleave', handleInteractionLeave);
        controls.addEventListener('mouseenter', handleInteractionEnter);
        controls.addEventListener('mouseleave', handleInteractionLeave);
        controls.addEventListener('focusin', handleInteractionEnter);
        controls.addEventListener('focusout', handleInteractionLeave);

        controls.addEventListener('keydown', function (event) {
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                handlePrev();
                prevButton.focus();
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                handleNext();
                nextButton.focus();
            }
        });

        applyActiveSlide();
        syncPlayPauseButton();
        restartTimerIfNeeded();
    }

    return {
        AUTOPLAY_INTERVAL_MS: AUTOPLAY_INTERVAL_MS,
        nextIndex: nextIndex,
        prevIndex: prevIndex,
        initialUserPaused: initialUserPaused,
        shouldAutoplay: shouldAutoplay,
        bootstrap: bootstrap
    };
});
