// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/board-file-upload.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    UPLOAD_URL,
    THUMBNAIL_FILE_TYPE,
    ATTACHMENT_FILE_TYPE,
    buildUploadFormData,
    mapUploadResponse,
    uploadFile
} = require('../../../main/resources/static/js/admin/board-file-upload.js');

test('UPLOAD_URL targets the common admin file upload endpoint', () => {
    assert.equal(UPLOAD_URL, '/api/admin/files');
});

test('THUMBNAIL_FILE_TYPE is IMAGE and ATTACHMENT_FILE_TYPE is ATTACHMENT', () => {
    assert.equal(THUMBNAIL_FILE_TYPE, 'IMAGE');
    assert.equal(ATTACHMENT_FILE_TYPE, 'ATTACHMENT');
});

test('buildUploadFormData includes the file and the requested fileType', () => {
    const file = new Blob(['fake-bytes'], { type: 'image/png' });

    const thumbnailForm = buildUploadFormData(file, THUMBNAIL_FILE_TYPE);
    assert.equal(thumbnailForm.get('fileType'), 'IMAGE');
    assert.equal(thumbnailForm.get('file').size, file.size);

    const attachmentForm = buildUploadFormData(file, ATTACHMENT_FILE_TYPE);
    assert.equal(attachmentForm.get('fileType'), 'ATTACHMENT');
});

test('mapUploadResponse resolves to the uploaded file url on success', () => {
    const body = {
        success: true,
        data: {
            id: 1,
            originalName: 'guide.zip',
            url: '/api/files/1',
            contentType: 'application/zip',
            size: 1234,
            fileType: 'ATTACHMENT',
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
                    originalName: 'thumb.png',
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

    const url = await uploadFile(file, THUMBNAIL_FILE_TYPE, fakeAdminFetch);

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/api/admin/files');
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.body.get('fileType'), 'IMAGE');
    assert.equal(url, '/api/files/1');
});

test('templates/admin/board/form.html wires thumbnail/attachment uploads to the shared File API', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/board/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/board-file-upload\.js/);
    assert.match(html, /AdminBoardFileUpload\.uploadFile\(file, AdminBoardFileUpload\.THUMBNAIL_FILE_TYPE, AdminFetch\.adminFetch\)/);
    assert.match(html, /AdminBoardFileUpload\.uploadFile\(file, AdminBoardFileUpload\.ATTACHMENT_FILE_TYPE, AdminFetch\.adminFetch\)/);
    assert.match(html, /#thumbnail'\)\.value = url/);
    assert.match(html, /#attachment'\)\.value = url/);
});

test('templates/admin/board/form.html submits thumbnail/attachment fields to AdminBoardController via common fetch', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/board/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /AdminFetch\.adminFetch\(url, \{/);
    assert.match(html, /\/api\/admin\/boards/);
    assert.match(html, /thumbnail: document\.querySelector\('#thumbnail'\)\.value \|\| null/);
    assert.match(html, /attachment: document\.querySelector\('#attachment'\)\.value \|\| null/);
});
