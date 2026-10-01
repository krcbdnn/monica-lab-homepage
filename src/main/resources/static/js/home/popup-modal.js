(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.PopupModal = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var STORAGE_KEY_PREFIX = 'popup-hide-until:';
    var MAX_VISIBLE_POPUPS = 3;

    // 위치/오프셋 상수. --space-4(2.5rem=40px)와 맞춘 오프셋 - 24px보다 뚜렷하게 분리되면서
    // 기존 spacing 토큰 스케일과도 맞는 값(Docker 8088 1024x768/1440x900 실측으로 확정).
    var RANK_OFFSET = 40;
    var DESKTOP_TOP_MIN = 96;
    var DESKTOP_TOP_VH_RATIO = 0.12;
    var MOBILE_BREAKPOINT = 480;
    var MOBILE_TOP_MIN = 96;

    // z-index: rank 기반(드래그 전) 값은 이 범위, 드래그로 앞으로 가져온 카드는 항상 이보다 높은
    // 값을 받는다 - "드래그한 Popup은 즉시 다른 Popup보다 최상단"을 z-index 수치 자체로 보장한다.
    var RANK_Z_BASE = 10;
    var DRAG_Z_START = 100;

    function pad2(value) {
        return value < 10 ? '0' + value : '' + value;
    }

    // 브라우저 로컬 날짜(YYYY-MM-DD)를 그대로 문자열로 만든다. Date#toISOString()은 UTC 기준이라
    // 자정 근처에서 로컬 날짜와 어긋날 수 있어 의도적으로 쓰지 않는다.
    function todayLocalDateString(date) {
        var d = date || new Date();
        return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
    }

    // storage 접근 자체가 실패해도(비공개 모드 등) "숨김 아님"으로 fail-open한다 -
    // 저장소 오류 때문에 콘텐츠가 영구히 숨겨지는 사고를 막기 위함이다.
    function isHiddenToday(storage, popupId, todayString) {
        try {
            return storage.getItem(STORAGE_KEY_PREFIX + popupId) === todayString;
        } catch (e) {
            return false;
        }
    }

    // 쓰기 실패는 조용히 무시한다. 호출부는 실패 여부와 무관하게 "지금 화면 닫기"는 계속 진행한다.
    function hideForToday(storage, popupId, todayString) {
        try {
            storage.setItem(STORAGE_KEY_PREFIX + popupId, todayString);
        } catch (e) {
            // 저장 실패해도 이번 화면 닫기 자체는 계속 진행(호출부 책임).
        }
    }

    // 오늘 숨김 대상만 걸러내고 나머지는 입력 순서(=서버가 정렬한 DOM 순서, createdAt DESC)를 그대로 보존한다.
    function selectVisiblePopupIds(allIds, storage, todayString) {
        return allIds.filter(function (id) {
            return !isHiddenToday(storage, id, todayString);
        });
    }

    // pool(오늘 숨김 제외한 노출 후보, createdAt DESC 순서)에서 이번 방문 중 닫은(dismissedIds) 것을
    // 제외하고 앞에서부터 최대 maxVisible개를 뽑는다. index 0이 항상 "현재 노출 중인 것 가운데 가장
    // 최신"이 되도록 매번 이 함수로 다시 계산한다(고정된 자리를 다음 팝업이 대신 채우는 방식이 아니라,
    // 남은 후보 전체를 다시 최신순으로 앞에서부터 채우는 방식) - 그래야 "가장 위 = 항상 최신",
    // "ESC를 반복하면 최신순으로 하나씩 닫힘" 계약이 항상 성립한다. 하나가 닫히면 다음 대기 Popup이
    // 자연스럽게 그 결과 목록에 포함되어 다시 최대 maxVisible개를 채운다.
    function selectPopupsToShow(pool, dismissedIds, maxVisible) {
        var shown = [];
        for (var i = 0; i < pool.length && shown.length < maxVisible; i++) {
            if (dismissedIds.indexOf(pool[i]) === -1) {
                shown.push(pool[i]);
            }
        }
        return shown;
    }

    // 카드 전체가 항상 viewport 안에 머물도록 좌표를 clamp한다. width/height가 viewport보다 큰 극단
    // 케이스에서도 NaN이나 뒤집힌 범위 없이 안전하게 동작한다(그 경우 카드가 viewport 한쪽 끝에 붙는
    // 형태로 수렴 - 완전히 화면 밖으로 나가지는 않는다).
    function clampPosition(x, y, width, height, viewportWidth, viewportHeight) {
        var minX = Math.min(0, viewportWidth - width);
        var maxX = Math.max(0, viewportWidth - width);
        var minY = Math.min(0, viewportHeight - height);
        var maxY = Math.max(0, viewportHeight - height);
        return {
            x: Math.min(Math.max(x, minX), maxX),
            y: Math.min(Math.max(y, minY), maxY)
        };
    }

    // rank(0/1/2)별 기본 위치. 데스크톱은 수평 중앙을 기준으로 rank마다 오른쪽/아래로 RANK_OFFSET씩,
    // 모바일(480px 미만)은 가로 offset 없이 세로로만 RANK_OFFSET씩 쌓는다. 항상 clampPosition을 거쳐
    // viewport를 벗어나지 않는다.
    function computeDefaultPosition(rank, viewportWidth, viewportHeight, cardWidth, cardHeight) {
        var isMobile = viewportWidth < MOBILE_BREAKPOINT;
        var baseLeft = (viewportWidth - cardWidth) / 2;

        if (isMobile) {
            var mobileTop = Math.max(MOBILE_TOP_MIN, viewportHeight * DESKTOP_TOP_VH_RATIO) + rank * RANK_OFFSET;
            return clampPosition(baseLeft, mobileTop, cardWidth, cardHeight, viewportWidth, viewportHeight);
        }

        var desktopTop = Math.max(DESKTOP_TOP_MIN, viewportHeight * DESKTOP_TOP_VH_RATIO) + rank * RANK_OFFSET;
        var desktopLeft = baseLeft + rank * RANK_OFFSET;
        return clampPosition(desktopLeft, desktopTop, cardWidth, cardHeight, viewportWidth, viewportHeight);
    }

    // 현재 노출 중인 Popup 가운데 "논리적으로 가장 위"(z-index가 가장 높은 것)를 찾는다. 아무도
    // 드래그하지 않았다면 rank 0(shown[0])이 그대로 최상단이고, 하나라도 드래그된 적이 있으면
    // zIndexOverrides 값이 가장 큰(=가장 최근에 앞으로 가져와진) 것이 최상단이 된다. ESC가 항상
    // "실제로 화면에서 가장 위에 보이는 카드"를 닫도록, 드래그 여부와 무관하게 이 함수 하나로 판정한다.
    function currentTopmostPopupId(shownIds, zIndexOverrides) {
        if (shownIds.length === 0) {
            return null;
        }
        var topmost = shownIds[0];
        var topmostOverride = zIndexOverrides.hasOwnProperty(topmost) ? zIndexOverrides[topmost] : -1;
        for (var i = 1; i < shownIds.length; i++) {
            var id = shownIds[i];
            var override = zIndexOverrides.hasOwnProperty(id) ? zIndexOverrides[id] : -1;
            if (override > topmostOverride) {
                topmost = id;
                topmostOverride = override;
            }
        }
        return topmost;
    }

    function bootstrap(doc, win) {
        var overlay = doc.getElementById('popup-overlay');
        if (!overlay) {
            return;
        }

        var modals = Array.prototype.slice.call(overlay.querySelectorAll('.popup-modal'));
        if (modals.length === 0) {
            return;
        }

        var storage = win.localStorage;
        var todayString = todayLocalDateString(new Date());
        var allIds = modals.map(function (modal) {
            return modal.getAttribute('data-popup-id');
        });
        var pool = selectVisiblePopupIds(allIds, storage, todayString);
        var dismissedIds = [];

        // 드래그된 적 있는 Popup만 기록한다(popupId -> z-index). 이 안에 들어있다는 것 자체가
        // "위치/앞뒤 순서를 사용자가 직접 조정했으니 render()가 되돌리면 안 된다"는 표식이다.
        var zIndexOverrides = {};
        var dragZCounter = DRAG_Z_START;

        function modalFor(id) {
            var found = null;
            modals.forEach(function (modal) {
                if (modal.getAttribute('data-popup-id') === id) {
                    found = modal;
                }
            });
            return found;
        }

        function bringToFront(popupId, modal) {
            dragZCounter += 1;
            zIndexOverrides[popupId] = dragZCounter;
            modal.style.zIndex = String(dragZCounter);
        }

        function render() {
            var shown = selectPopupsToShow(pool, dismissedIds, MAX_VISIBLE_POPUPS);
            // overlay 자신이 아직 hidden(=display:none)이면 그 아래 모든 카드의 offsetWidth/Height가
            // 0으로 측정된다 - 위치 계산 전에 overlay부터 먼저 보이게 해야 카드 실제 크기를 알 수 있다.
            overlay.hidden = shown.length === 0;
            modals.forEach(function (modal) {
                var popupId = modal.getAttribute('data-popup-id');
                var rank = shown.indexOf(popupId);
                if (rank === -1) {
                    modal.hidden = true;
                    return;
                }
                modal.hidden = false;
                // 드래그로 위치/z-index를 직접 옮긴 Popup은 재배치하지 않는다 - 다른 Popup이 닫히거나
                // 보충돼도 방문 동안 사용자가 옮긴 자리 그대로 유지된다.
                if (!zIndexOverrides.hasOwnProperty(popupId)) {
                    modal.style.zIndex = String(RANK_Z_BASE + (MAX_VISIBLE_POPUPS - rank));
                    var position = computeDefaultPosition(
                        rank, win.innerWidth, win.innerHeight, modal.offsetWidth, modal.offsetHeight);
                    modal.style.left = position.x + 'px';
                    modal.style.top = position.y + 'px';
                }
            });
            return shown;
        }

        function dismiss(popupId) {
            dismissedIds.push(popupId);
            render();
        }

        render();

        var dragEnabled = win.innerWidth >= MOBILE_BREAKPOINT;

        modals.forEach(function (modal) {
            var popupId = modal.getAttribute('data-popup-id');
            var closeButton = modal.querySelector('.popup-modal__close');
            var hideTodayButton = modal.querySelector('.popup-modal__hide-today');
            var header = modal.querySelector('.popup-modal__header');

            if (closeButton) {
                closeButton.addEventListener('click', function () {
                    dismiss(popupId);
                });
            }
            if (hideTodayButton) {
                hideTodayButton.addEventListener('click', function () {
                    hideForToday(storage, popupId, todayString);
                    dismiss(popupId);
                });
            }

            // 모바일(480px 미만)에서는 drag 리스너 자체를 등록하지 않는다 - 세로 스크롤 제스처와
            // 충돌시키지 않기 위해서다(bootstrap 시점 1회 판정, 화면 회전 등 이후 변화는 반영하지 않음).
            if (header && dragEnabled) {
                // 터치 기기의 브라우저 기본 스크롤/팬 제스처가 Pointer Events drag를 가로채지 않도록
                // 한다. dragEnabled(480px 이상)에서만 적용되므로 모바일 스크롤에는 영향이 없다.
                header.style.touchAction = 'none';
                var dragging = false;
                var startPointerX = 0;
                var startPointerY = 0;
                var startLeft = 0;
                var startTop = 0;

                header.addEventListener('pointerdown', function (event) {
                    if (event.target.closest && event.target.closest('button')) {
                        return;
                    }
                    dragging = true;
                    var rect = modal.getBoundingClientRect();
                    startLeft = rect.left;
                    startTop = rect.top;
                    startPointerX = event.clientX;
                    startPointerY = event.clientY;
                    bringToFront(popupId, modal);
                    if (header.setPointerCapture) {
                        header.setPointerCapture(event.pointerId);
                    }
                });

                header.addEventListener('pointermove', function (event) {
                    if (!dragging) {
                        return;
                    }
                    var dx = event.clientX - startPointerX;
                    var dy = event.clientY - startPointerY;
                    var next = clampPosition(
                        startLeft + dx, startTop + dy,
                        modal.offsetWidth, modal.offsetHeight,
                        win.innerWidth, win.innerHeight);
                    modal.style.left = next.x + 'px';
                    modal.style.top = next.y + 'px';
                });

                function endDrag(event) {
                    if (!dragging) {
                        return;
                    }
                    dragging = false;
                    if (header.hasPointerCapture && header.hasPointerCapture(event.pointerId)) {
                        header.releasePointerCapture(event.pointerId);
                    }
                }

                header.addEventListener('pointerup', endDrag);
                header.addEventListener('pointercancel', endDrag);
            }
        });

        // ESC: 현재 화면에 보이는 Popup 가운데 실제로 z-index가 가장 높은(=논리적으로 가장 위) 1건만
        // 닫는다. 드래그로 순서가 바뀌었어도 currentTopmostPopupId가 그 상태를 그대로 반영한다.
        doc.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') {
                return;
            }
            var shown = selectPopupsToShow(pool, dismissedIds, MAX_VISIBLE_POPUPS);
            var topmost = currentTopmostPopupId(shown, zIndexOverrides);
            if (topmost !== null) {
                dismiss(topmost);
            }
        });
    }

    return {
        todayLocalDateString: todayLocalDateString,
        isHiddenToday: isHiddenToday,
        hideForToday: hideForToday,
        selectVisiblePopupIds: selectVisiblePopupIds,
        selectPopupsToShow: selectPopupsToShow,
        clampPosition: clampPosition,
        computeDefaultPosition: computeDefaultPosition,
        currentTopmostPopupId: currentTopmostPopupId,
        bootstrap: bootstrap
    };
});
