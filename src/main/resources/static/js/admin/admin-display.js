// P14-T9C-1: 관리자 목록/상세 화면 공통 presentation helper(표시명, semantic badge, inline status, empty/error row).
// URL/목록 state/pagination/fetch는 이 파일의 책임이 아니다. 로드 자체로는 DOM을 건드리지 않으며(side effect 없음),
// 모든 텍스트는 textContent로만 넣는다 - HTML 문자열을 조립하거나 반환하지 않는다.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminDisplay = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var BOARD_TYPE_LABELS = Object.freeze({
        NOTICE: '공지사항',
        GALLERY: '갤러리',
        ARCHIVE: '자료실',
        REVIEW: '강의 후기'
    });
    var PROGRAM_TYPE_LABELS = Object.freeze({COURSE: '정규 강좌', SPECIAL: '특강'});
    var RECRUIT_STATUS_LABELS = Object.freeze({OPEN: '모집중', CLOSED: '마감'});
    var FILE_TYPE_LABELS = Object.freeze({IMAGE: '이미지', ATTACHMENT: '첨부파일'});

    // 분류(type/category)는 neutral, 상태만 positive/muted/attention을 쓴다. 색만으로 의미를 전달하지 않도록
    // badge에는 항상 텍스트가 함께 들어간다.
    var TONES = Object.freeze(['positive', 'muted', 'neutral', 'attention']);
    var RECRUIT_STATUS_TONES = Object.freeze({OPEN: 'positive', CLOSED: 'muted'});

    function hasOwn(map, key) {
        return !!map && Object.prototype.hasOwnProperty.call(map, key);
    }

    function isBlank(value) {
        return value === null || value === undefined || value === '';
    }

    // 매핑에 없는 값은 숨기지 않고 raw 값 그대로, 값 자체가 없으면 '-'로 표시한다.
    function label(map, value) {
        if (isBlank(value)) {
            return '-';
        }
        return hasOwn(map, value) ? map[value] : String(value);
    }

    // REVIEW만 programType을 괄호로 덧붙인다: 강의 후기 / 강의 후기(정규 강좌) / 강의 후기(특강).
    function boardTypeLabel(boardType, programType) {
        var base = label(BOARD_TYPE_LABELS, boardType);
        if (boardType !== 'REVIEW' || isBlank(programType)) {
            return base;
        }
        return base + '(' + label(PROGRAM_TYPE_LABELS, programType) + ')';
    }

    function recruitStatusTone(status) {
        return hasOwn(RECRUIT_STATUS_TONES, status) ? RECRUIT_STATUS_TONES[status] : 'neutral';
    }

    function badgeClassName(tone) {
        return 'badge admin-badge admin-badge--' + (TONES.indexOf(tone) !== -1 ? tone : 'neutral');
    }

    // 서버(GlobalExceptionHandler)의 error.message는 ErrorCode의 사용자용 문구다(예외 상세 미포함). 없으면 fallback.
    function apiErrorMessage(body, fallback) {
        var message = body && body.error && body.error.message;
        return typeof message === 'string' && message ? message : fallback;
    }

    function createBadge(doc, text, tone) {
        var span = doc.createElement('span');
        span.className = badgeClassName(tone);
        span.textContent = isBlank(text) ? '-' : String(text);
        return span;
    }

    // 공개/비공개, 노출/비노출, 공개/숨김처럼 켜짐/꺼짐 두 상태만 갖는 값.
    function createStateBadge(doc, isOn, onText, offText) {
        return createBadge(doc, isOn ? onText : offText, isOn ? 'positive' : 'muted');
    }

    function removeChildren(el) {
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    }

    function replaceContent(el, node) {
        if (!el) {
            return;
        }
        removeChildren(el);
        el.appendChild(node);
    }

    // role을 먼저 지정한 뒤 텍스트를 넣어 보조기기가 변경을 인식하게 한다(role=status/alert가 이미 live region).
    function showStatus(el, message, type) {
        if (!el) {
            return;
        }
        var isError = type === 'error';
        el.className = 'alert ' + (isError ? 'alert-danger' : 'alert-success');
        el.setAttribute('role', isError ? 'alert' : 'status');
        el.hidden = false;
        el.textContent = message;
    }

    function clearStatus(el) {
        if (!el) {
            return;
        }
        el.hidden = true;
        el.textContent = '';
        el.removeAttribute('role');
        el.className = '';
    }

    function renderMessageRow(doc, tbody, colSpan, message, className) {
        removeChildren(tbody);
        var tr = doc.createElement('tr');
        var td = doc.createElement('td');
        td.className = className;
        td.setAttribute('colspan', String(colSpan));
        td.textContent = message;
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    function renderEmptyRow(doc, tbody, colSpan, message) {
        renderMessageRow(doc, tbody, colSpan, message, 'admin-list-empty');
    }

    function renderErrorRow(doc, tbody, colSpan, message) {
        renderMessageRow(doc, tbody, colSpan, message, 'admin-list-empty admin-list-empty--error');
    }

    return {
        BOARD_TYPE_LABELS: BOARD_TYPE_LABELS,
        PROGRAM_TYPE_LABELS: PROGRAM_TYPE_LABELS,
        RECRUIT_STATUS_LABELS: RECRUIT_STATUS_LABELS,
        FILE_TYPE_LABELS: FILE_TYPE_LABELS,
        TONES: TONES,
        label: label,
        boardTypeLabel: boardTypeLabel,
        recruitStatusTone: recruitStatusTone,
        badgeClassName: badgeClassName,
        apiErrorMessage: apiErrorMessage,
        createBadge: createBadge,
        createStateBadge: createStateBadge,
        replaceContent: replaceContent,
        showStatus: showStatus,
        clearStatus: clearStatus,
        renderEmptyRow: renderEmptyRow,
        renderErrorRow: renderErrorRow
    };
});
