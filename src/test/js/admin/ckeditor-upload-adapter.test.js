// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/ckeditor-upload-adapter.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    UPLOAD_URL,
    UPLOAD_FILE_TYPE,
    buildUploadFormData,
    mapUploadResponse,
    createUploadAdapter
} = require('../../../main/resources/static/js/admin/ckeditor-upload-adapter.js');

test('UPLOAD_URL targets the common admin file upload endpoint', () => {
    assert.equal(UPLOAD_URL, '/api/admin/files');
});

test('UPLOAD_FILE_TYPE is IMAGE', () => {
    assert.equal(UPLOAD_FILE_TYPE, 'IMAGE');
});

test('buildUploadFormData sets fileType=IMAGE and includes the file', () => {
    const file = new Blob(['fake-image-bytes'], { type: 'image/png' });
    const formData = buildUploadFormData(file);

    assert.equal(formData.get('fileType'), 'IMAGE');
    const storedFile = formData.get('file');
    assert.equal(storedFile.size, file.size);
    assert.equal(storedFile.type, file.type);
});

test('mapUploadResponse resolves to { default: url } on success', () => {
    const body = {
        success: true,
        data: {
            id: 1,
            originalName: 'photo.png',
            url: '/api/files/1',
            contentType: 'image/png',
            size: 1234,
            fileType: 'IMAGE',
            createdAt: '2026-08-08T00:00:00'
        },
        error: null
    };

    assert.deepEqual(mapUploadResponse(body), { default: '/api/files/1' });
});

test('mapUploadResponse throws when the upload failed', () => {
    const body = {
        success: false,
        data: null,
        error: { code: 'INVALID_FILE_TYPE', message: '허용되지 않은 파일 형식입니다.' }
    };

    assert.throws(() => mapUploadResponse(body), /허용되지 않은 파일 형식입니다\./);
});

test('createUploadAdapter.upload() posts to /api/admin/files with fileType=IMAGE via the provided fetch function', async () => {
    const file = new Blob(['fake-image-bytes'], { type: 'image/png' });
    const loader = { file: Promise.resolve(file) };
    const calls = [];
    const fakeAdminFetch = (url, options) => {
        calls.push({ url, options });
        return Promise.resolve({
            json: () => Promise.resolve({
                success: true,
                data: {
                    id: 1,
                    originalName: 'photo.png',
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

    const adapter = createUploadAdapter(loader, fakeAdminFetch);
    const result = await adapter.upload();

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/api/admin/files');
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.body.get('fileType'), 'IMAGE');
    assert.deepEqual(result, { default: '/api/files/1' });
});

test('createUploadAdapter.abort() does not throw', () => {
    const adapter = createUploadAdapter({ file: Promise.resolve(null) }, () => {});
    assert.doesNotThrow(() => adapter.abort());
});

test('templates/admin/page/form.html wires the shared upload adapter and common fetch util', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/page/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/ckeditor-upload-adapter\.js/);
    assert.match(html, /AdminCkeditorUploadAdapter\.installUploadAdapterPlugin\(/);
    assert.match(html, /AdminFetch\.adminFetch/);
});

test('templates/admin/program/form.html wires the shared upload adapter and common fetch util', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/program/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/ckeditor-upload-adapter\.js/);
    assert.match(html, /AdminCkeditorUploadAdapter\.installUploadAdapterPlugin\(/);
    assert.match(html, /AdminFetch\.adminFetch/);
});

test('templates/admin/board/form.html wires the shared upload adapter and common fetch util', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/board/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/ckeditor-upload-adapter\.js/);
    assert.match(html, /AdminCkeditorUploadAdapter\.installUploadAdapterPlugin\(/);
    assert.match(html, /AdminFetch\.adminFetch/);
});

test('templates/admin/popup/form.html wires the shared upload adapter and common fetch util', () => {
    const templatePath = path.join(__dirname, '../../../main/resources/templates/admin/popup/form.html');
    const html = fs.readFileSync(templatePath, 'utf8');

    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/ckeditor-upload-adapter\.js/);
    assert.match(html, /AdminCkeditorUploadAdapter\.installUploadAdapterPlugin\(/);
    assert.match(html, /AdminFetch\.adminFetch/);
});
