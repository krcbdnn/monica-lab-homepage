(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminFilePreview = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    function normalizeUrl(url) {
        if (typeof url !== 'string') {
            return '';
        }
        return url.trim();
    }

    function renderImagePreview(containerEl, imgEl, linkEl, url) {
        var normalized = normalizeUrl(url);

        if (!normalized) {
            containerEl.hidden = true;
            imgEl.removeAttribute('src');
            linkEl.removeAttribute('href');
            return;
        }

        imgEl.setAttribute('src', normalized);
        linkEl.setAttribute('href', normalized);
        containerEl.hidden = false;
    }

    function renderLinkPreview(containerEl, linkEl, url) {
        var normalized = normalizeUrl(url);

        if (!normalized) {
            containerEl.hidden = true;
            linkEl.removeAttribute('href');
            return;
        }

        linkEl.setAttribute('href', normalized);
        containerEl.hidden = false;
    }

    function extractFileIdFromUrl(url) {
        var normalized = normalizeUrl(url);
        var match = normalized.match(/^\/api\/files\/(\d+)$/);
        return match ? match[1] : null;
    }

    // 파일명 조회는 renderLinkPreview가 이미 만든 링크/hidden 상태와 완전히 독립적으로 동작한다.
    // 조회에 실패하거나 originalName이 없으면 nameWrapEl을 hidden 상태로 남겨(기본값) 링크만 노출되던
    // 기존 화면으로 조용히 되돌아간다 - "현재 첨부파일: (열기/다운로드)"처럼 이름이 빈 채로 보이는 것을 방지한다.
    function loadAttachmentName(nameWrapEl, nameEl, url, adminFetchFn) {
        nameWrapEl.hidden = true;
        nameEl.textContent = '';

        var fileId = extractFileIdFromUrl(url);
        if (!fileId) {
            return Promise.resolve();
        }

        return adminFetchFn('/api/admin/files/' + fileId)
            .then(function (response) {
                return response.ok ? response.json() : null;
            })
            .then(function (body) {
                if (body && body.success && body.data && body.data.originalName) {
                    nameEl.textContent = body.data.originalName;
                    nameWrapEl.hidden = false;
                }
            })
            .catch(function () {
                // 조회 실패 시 nameWrapEl은 hidden 상태로 유지된다(링크만 노출).
            });
    }

    return {
        normalizeUrl: normalizeUrl,
        renderImagePreview: renderImagePreview,
        renderLinkPreview: renderLinkPreview,
        extractFileIdFromUrl: extractFileIdFromUrl,
        loadAttachmentName: loadAttachmentName
    };
});
