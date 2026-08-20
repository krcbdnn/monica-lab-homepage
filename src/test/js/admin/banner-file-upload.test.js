// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/banner-file-upload.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    UPLOAD_URL,
    IMAGE_FILE_TYPE,
    buildUploadFormData,
    mapUploadResponse,
    uploadFile
} = require('../../../main/resources/static/js/admin/banner-file-upload.js');

test('UPLOAD_URL targets the common admin file upload endpoint', () => {
    assert.equal(UPLOAD_URL, '/api/admin/files');
});

test('IMAGE_FILE_TYPE is IMAGE', () => {
    assert.equal(IMAGE_FILE_TYPE, 'IMAGE');
});

test('buildUploadFormData includes the file and the requested fileType', () => {
    const file = new Blob(['fake-bytes'], { type: 'image/png' });

    const formData = buildUploadFormData(file, IMAGE_FILE_TYPE);
    assert.equal(formData.get('fileType'), 'IMAGE');
    assert.equal(formData.get('file').size, file.size);
});

test('mapUploadResponse resolves to the uploaded file url on success', () => {
    const body = {
        success: true,
        data: {
            id: 1,
            originalName: 'banner.png',
            url: '/api/files/1',
            contentType: 'image/png',
            size: 1234,
            fileType: 'IMAGE',
            createdAt: '2026-08-08T00:00:00'
        },
        error: null
    };

    assert.equal(mapUploadResponse(body), '/api/files/1');
});

test('mapUploadResponse throws when the upload failed', () => {
    const body = {
        success: false,
        data: null,
        error: { code: 'INVALID_FILE_TYPE', message: '허용되지 않은 파일 형식입니다.' }
    };

    assert.throws(() => mapUploadResponse(body), /허용되지 않은 파일 형식입니다\./);
});

test('uploadFile posts to /api/admin/files with the requested fileType via the provided fetch function', async () => {
    const file = new Blob(['fake-image-bytes'], { type: 'image/png' });
    const calls = [];
    const fakeAdminFetch = (url, options) => {
        calls.push({ url, options });
        return Promise.resolve({
            json: () => Promise.resolve({
                success: true,
                data: {
                    id: 1,
                    originalName: 'banner.png',
                    url: '/api/files/1',
                    contentType: 'image/png',
                    size: 10,
                    fileType: 'IMAGE',
                    createdAt: '2026-08-08T00:00:00'
                },
                error: null
            })
        });
    };

    const url = await uploadFile(file, IMAGE_FILE_TYPE, fakeAdminFetch);

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/api/admin/files');
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.body.get('fileType'), 'IMAGE');
    assert.equal(url, '/api/files/1');
});

test('templates/admin/banner/form.html wires image upload to the shared File API', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/banner/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/banner-file-upload\.js/);
    assert.match(html, /AdminBannerFileUpload\.uploadFile\(file, AdminBannerFileUpload\.IMAGE_FILE_TYPE, AdminFetch\.adminFetch\)/);
    assert.match(html, /#image'\)\.value = url/);
});

test('templates/admin/banner/form.html submits banner fields to AdminBannerController via common fetch', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/banner/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /AdminFetch\.adminFetch\(url, \{/);
    assert.match(html, /\/api\/admin\/banners/);
    assert.match(html, /image: document\.querySelector\('#image'\)\.value \|\| null/);
    assert.match(html, /sortOrder: Number\(document\.querySelector\('#sortOrder'\)\.value\)/);
});
