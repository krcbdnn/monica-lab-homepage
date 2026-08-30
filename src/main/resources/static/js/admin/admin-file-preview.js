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

    return {
        normalizeUrl: normalizeUrl,
        renderImagePreview: renderImagePreview,
        renderLinkPreview: renderLinkPreview
    };
});
