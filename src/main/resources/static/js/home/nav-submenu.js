(function () {
    'use strict';

    // P13-T30B: 열려 있어야 할 그룹을 하나로 결정하는 순수 함수. 같은 그룹을 다시 활성화하면
    // 닫고(null), 다른 그룹을 활성화하면 그 그룹만 연다(기존 열린 그룹은 자동으로 닫힌 것으로
    // 간주 - "다른 GROUP을 열면 기존 GROUP은 닫힌다"는 계약을 hover/click 모두가 공유한다).
    function resolveOpenGroupId(currentOpenId, targetId) {
        return currentOpenId === targetId ? null : targetId;
    }

    function init(doc, win) {
        var groups = doc.querySelectorAll('.site-nav__item.has-submenu');
        if (!groups.length) {
            return;
        }

        var supportsHover = !!(win && typeof win.matchMedia === 'function'
            && win.matchMedia('(hover: hover) and (pointer: fine)').matches);

        var openGroupId = null;
        var openGroupEl = null;
        // 지금 열려 있는 그룹이 (아직 click이 손대기 전) hover만으로 열린 것인지 추적한다. 실제
        // 마우스 사용자가 trigger를 클릭하려면 반드시 먼저 포인터를 그 위로 옮겨야 하므로, click
        // 이벤트보다 mouseenter가 항상 먼저 발생해 이미 openGroup(hover)로 열어 둔 상태가 된다.
        // 이 상태를 click이 곧바로 다시 닫아버리면 마우스로는 클릭으로 절대 열 수 없으므로, 이번
        // 클릭은 "이미 열려 있게 그대로 둔다"로 처리하고 hover 기원 플래그만 해제한다 - 포인터를
        // 움직이지 않은 채 같은 자리에서 한 번 더 클릭(명시적으로 닫으려는 의도)하면 그때는
        // resolveOpenGroupId를 통해 정상적으로 닫힌다. 키보드 Enter/Space나 hover가 없는 터치
        // tap은 애초에 앞서 mouseenter가 없으므로 이 예외와 무관하게 즉시 정상 토글된다.
        var openedByHover = false;

        function setGroupOpen(groupEl, open) {
            var trigger = groupEl.querySelector('.site-nav__trigger');
            groupEl.classList.toggle('is-open', open);
            if (trigger) {
                trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            }
        }

        function closeOpenGroup() {
            if (openGroupEl) {
                setGroupOpen(openGroupEl, false);
            }
            openGroupId = null;
            openGroupEl = null;
            openedByHover = false;
        }

        function openGroup(groupId, groupEl, byHover) {
            if (openGroupEl && openGroupEl !== groupEl) {
                setGroupOpen(openGroupEl, false);
            }
            setGroupOpen(groupEl, true);
            openGroupId = groupId;
            openGroupEl = groupEl;
            openedByHover = !!byHover;
        }

        function activateGroup(groupId, groupEl) {
            if (openGroupId === groupId && openedByHover) {
                openedByHover = false;
                return;
            }
            var nextOpenId = resolveOpenGroupId(openGroupId, groupId);
            if (nextOpenId === null) {
                closeOpenGroup();
            } else {
                openGroup(groupId, groupEl, false);
            }
        }

        groups.forEach(function (groupEl, index) {
            var groupId = groupEl.getAttribute('data-menu-id') || String(index);
            var trigger = groupEl.querySelector('.site-nav__trigger');

            // P13-T30B: mouseenter/mouseleave는 trigger 하나가 아니라 .has-submenu 전체(trigger +
            // submenu)를 기준으로 건다 - trigger에서 submenu로 pointer를 옮길 때 자식 이동으로 인한
            // mouseout이 반복 발생해 닫히는 문제를 피하기 위함(mouseenter/leave는 버블링하지 않고
            // 이 요소의 실제 경계를 벗어날 때만 발생한다). hover가 없는 터치 기기에서는 tap이
            // mouseenter를 합성 발생시켜 뒤이은 click과 충돌(열자마자 다시 닫힘)할 수 있어
            // matchMedia(hover: hover)로 실제 hover 가능한 기기에서만 바인딩한다.
            if (supportsHover) {
                groupEl.addEventListener('mouseenter', function () {
                    openGroup(groupId, groupEl, true);
                });
                groupEl.addEventListener('mouseleave', function () {
                    if (openGroupId === groupId) {
                        closeOpenGroup();
                    }
                });
            }

            if (trigger) {
                trigger.addEventListener('click', function () {
                    activateGroup(groupId, groupEl);
                });
            }
        });

        doc.addEventListener('click', function (event) {
            if (openGroupEl && !openGroupEl.contains(event.target)) {
                closeOpenGroup();
            }
        });

        doc.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && openGroupEl) {
                var trigger = openGroupEl.querySelector('.site-nav__trigger');
                closeOpenGroup();
                if (trigger) {
                    trigger.focus();
                }
            }
        });

        // P13-T30B: hamburger(#nav-toggle)를 닫으면 열려 있던 submenu도 초기화한다. nav-toggle.js를
        // 수정하거나 custom event/전역 상태를 새로 만들지 않고, 같은 버튼의 click을 함께 구독해
        // nav-toggle.js의 리스너가 먼저 갱신해 둔 aria-expanded 값을 그대로 읽기만 한다
        // (두 파일의 <script> 로드 순서상 nav-toggle.js가 먼저 리스너를 등록하므로, 같은 클릭
        // 이벤트에서 nav-toggle의 처리가 항상 먼저 끝난 뒤 이 리스너가 실행된다).
        var navToggle = doc.getElementById('nav-toggle');
        if (navToggle) {
            navToggle.addEventListener('click', function () {
                if (navToggle.getAttribute('aria-expanded') === 'false') {
                    closeOpenGroup();
                }
            });
        }
    }

    if (typeof module === 'object' && module.exports) {
        module.exports = { resolveOpenGroupId: resolveOpenGroupId };
    }

    if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () {
                init(document, window);
            });
        } else {
            init(document, window);
        }
    }
})();
