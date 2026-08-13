(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminHeader = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var ME_URL = '/api/admin/me';
    var NAME_ELEMENT_ID = 'admin-name';

    function applyAdminName(nameElement, responseBody) {
        if (!nameElement || !responseBody || !responseBody.data) {
            return;
        }
        nameElement.textContent = responseBody.data.name;
    }

    function bootstrap(adminFetch, doc) {
        var nameElement = doc.getElementById(NAME_ELEMENT_ID);
        return adminFetch(ME_URL)
            .then(function (response) {
                return response.json();
            })
            .then(function (body) {
                applyAdminName(nameElement, body);
            });
    }

    return {
        ME_URL: ME_URL,
        NAME_ELEMENT_ID: NAME_ELEMENT_ID,
        applyAdminName: applyAdminName,
        bootstrap: bootstrap
    };
});
