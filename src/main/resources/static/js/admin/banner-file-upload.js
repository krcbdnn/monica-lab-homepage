(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminBannerFileUpload = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var UPLOAD_URL = '/api/admin/files';
    var IMAGE_FILE_TYPE = 'IMAGE';

    function buildUploadFormData(file, fileType) {
        var formData = new FormData();
        formData.append('file', file);
        formData.append('fileType', fileType);
        return formData;
    }

    function mapUploadResponse(responseBody) {
        if (!responseBody || responseBody.success !== true || !responseBody.data || !responseBody.data.url) {
            var message = (responseBody && responseBody.error && responseBody.error.message)
                || '파일 업로드에 실패했습니다.';
            throw new Error(message);
        }
        return responseBody.data.url;
    }

    function uploadFile(file, fileType, adminFetch) {
        return adminFetch(UPLOAD_URL, {
            method: 'POST',
            body: buildUploadFormData(file, fileType)
        })
            .then(function (response) {
                return response.json();
            })
            .then(function (body) {
                return mapUploadResponse(body);
            });
    }

    return {
        UPLOAD_URL: UPLOAD_URL,
        IMAGE_FILE_TYPE: IMAGE_FILE_TYPE,
        buildUploadFormData: buildUploadFormData,
        mapUploadResponse: mapUploadResponse,
        uploadFile: uploadFile
    };
});
