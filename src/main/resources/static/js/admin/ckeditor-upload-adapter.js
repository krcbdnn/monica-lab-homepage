(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminCkeditorUploadAdapter = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var UPLOAD_URL = '/api/admin/files';
    var UPLOAD_FILE_TYPE = 'IMAGE';

    function buildUploadFormData(file) {
        var formData = new FormData();
        formData.append('file', file);
        formData.append('fileType', UPLOAD_FILE_TYPE);
        return formData;
    }

    function mapUploadResponse(responseBody) {
        if (!responseBody || responseBody.success !== true || !responseBody.data || !responseBody.data.url) {
            var message = (responseBody && responseBody.error && responseBody.error.message)
                || '이미지 업로드에 실패했습니다.';
            throw new Error(message);
        }
        return { default: responseBody.data.url };
    }

    function createUploadAdapter(loader, adminFetch) {
        return {
            upload: function () {
                return loader.file.then(function (file) {
                    return adminFetch(UPLOAD_URL, {
                        method: 'POST',
                        body: buildUploadFormData(file)
                    })
                        .then(function (response) {
                            return response.json();
                        })
                        .then(function (body) {
                            return mapUploadResponse(body);
                        });
                });
            },
            abort: function () {}
        };
    }

    function installUploadAdapterPlugin(editor, adminFetch) {
        editor.plugins.get('FileRepository').createUploadAdapter = function (loader) {
            return createUploadAdapter(loader, adminFetch);
        };
    }

    return {
        UPLOAD_URL: UPLOAD_URL,
        UPLOAD_FILE_TYPE: UPLOAD_FILE_TYPE,
        buildUploadFormData: buildUploadFormData,
        mapUploadResponse: mapUploadResponse,
        createUploadAdapter: createUploadAdapter,
        installUploadAdapterPlugin: installUploadAdapterPlugin
    };
});
