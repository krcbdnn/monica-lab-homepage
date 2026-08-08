(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminFetch = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var CSRF_COOKIE_NAME = 'XSRF-TOKEN';
    var CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
    var STATE_CHANGING_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

    function parseCookie(cookieHeader, name) {
        if (!cookieHeader) {
            return null;
        }
        var cookies = cookieHeader.split(';');
        for (var i = 0; i < cookies.length; i++) {
            var parts = cookies[i].split('=');
            var key = parts.shift().trim();
            if (key === name) {
                return decodeURIComponent(parts.join('=').trim());
            }
        }
        return null;
    }

    function buildHeaders(method, baseHeaders, csrfToken) {
        var headers = {};
        var key;
        for (key in baseHeaders) {
            if (Object.prototype.hasOwnProperty.call(baseHeaders, key)) {
                headers[key] = baseHeaders[key];
            }
        }
        var normalizedMethod = (method || 'GET').toUpperCase();
        if (csrfToken && STATE_CHANGING_METHODS.indexOf(normalizedMethod) !== -1) {
            headers[CSRF_HEADER_NAME] = csrfToken;
        }
        return headers;
    }

    function adminFetch(url, options) {
        options = options || {};
        var method = options.method || 'GET';
        var cookieHeader = typeof document !== 'undefined' ? document.cookie : '';
        var csrfToken = parseCookie(cookieHeader, CSRF_COOKIE_NAME);
        var headers = buildHeaders(method, options.headers, csrfToken);

        var fetchOptions = {};
        var key;
        for (key in options) {
            if (Object.prototype.hasOwnProperty.call(options, key)) {
                fetchOptions[key] = options[key];
            }
        }
        fetchOptions.headers = headers;
        fetchOptions.credentials = fetchOptions.credentials || 'same-origin';

        return fetch(url, fetchOptions);
    }

    return {
        CSRF_COOKIE_NAME: CSRF_COOKIE_NAME,
        CSRF_HEADER_NAME: CSRF_HEADER_NAME,
        parseCookie: parseCookie,
        buildHeaders: buildHeaders,
        adminFetch: adminFetch
    };
});
