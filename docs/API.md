# API.md

# REST API Specification

Version 2.0

---

# Base URL

`/api`

---

# 인증 / 공통 계약

- 공개 API는 인증 없이 호출한다.
- 관리자 API(`/api/admin/**`)는 세션 인증 + `ROLE_ADMIN`이 필요하다.
- JSON 요청은 `Content-Type: application/json`, 파일 업로드만 `multipart/form-data`를 사용한다.
- 모든 JSON 성공/실패 응답은 ARCHITECTURE.md의 `ApiResponse` 구조를 따른다. `204 No Content`는 body가 없다.
- 날짜/시간은 ISO-8601 `yyyy-MM-dd'T'HH:mm:ss` 형식이다.
- enum에 정의되지 않은 값, 형식 오류, Validation 실패는 `INVALID_INPUT_VALUE`(400)이다.
- 관리자 화면은 공개 API를 조회 데이터 소스로 재사용하지 않는다. 반드시 해당 `/api/admin/**` GET API를 사용한다.

## 페이징 / 정렬 공통 규칙

페이징 목록 API의 공통 Query:

| 이름 | 타입 | 필수 | 기본값 | Validation | 설명 |
|---|---|---:|---|---|---|
| page | Integer | N | 0 | `>= 0` | 0-based page |
| size | Integer | N | 20 | `1..100` | page size |
| sort | String | N | `createdAt,DESC` | `{field},{ASC\|DESC}` | 허용 필드는 각 API에 명시 |

페이징 응답의 `data`는 ARCHITECTURE.md의 `PageResponse<T>` 구조(`content`, `page`, `size`, `totalElements`, `totalPages`, `last`)를 사용한다.

## 공통 상태 코드

- 생성 성공: 201
- 조회/수정/PATCH 성공: 200
- 삭제 성공: 204
- Validation/잘못된 enum/잘못된 query: `INVALID_INPUT_VALUE` 400
- 미인증 관리자 API: `UNAUTHORIZED` 401
- 인증 실패: `AUTHENTICATION_FAILED` 401
- 권한 부족: `ACCESS_DENIED` 403
- 도메인 리소스 없음: `{DOMAIN}_NOT_FOUND` 404
- 정의되지 않은 서버 오류: `INTERNAL_SERVER_ERROR` 500

---

# Authentication

## POST /api/admin/login

인증: 불필요

Request `AdminLoginRequest`

| field | type | required | Validation |
|---|---|---:|---|
| loginId | String | Y | `@NotBlank`, max 50 |
| password | String | Y | `@NotBlank` |

```json
{
  "loginId": "admin",
  "password": "********"
}
```

Response 200: `data`는 `id`, `loginId`, `name`, `role`을 가진 관리자 정보 객체. 성공 시 세션을 생성한다.

Errors: `INVALID_INPUT_VALUE`(400), `AUTHENTICATION_FAILED`(401). 로그인 실패 사유로 계정 존재 여부를 노출하지 않는다.

## POST /api/admin/logout

인증: ROLE_ADMIN

Request body 없음. 세션을 무효화한다.

Response 200: `ApiResponse.success(null)`.

## GET /api/admin/me

인증: ROLE_ADMIN

Response 200 `AdminResponse`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "loginId": "admin",
    "name": "관리자",
    "role": "ROLE_ADMIN"
  },
  "error": null
}
```

다른 관리자 계정을 조회/등록/수정하는 API는 제공하지 않는다.

---

# Dashboard

## GET /api/admin/dashboard

인증: ROLE_ADMIN

Response 200 `DashboardResponse.data`

| field | type | 설명 |
|---|---|---|
| recentBoards | Array<BoardSummaryResponse> | 공개/비공개 전체 게시글 중 `createdAt DESC` 최대 5건 |
| programStatus.OPEN | Long | 모집중 프로그램 수 |
| programStatus.CLOSED | Long | 마감 프로그램 수 |
| quickMenus | Array<QuickMenuResponse> | 아래 고정 관리자 내부 링크 6개. 각 항목은 `label`, `url` |

`BoardSummaryResponse`: `id`, `boardType`, `title`, `isPublic`, `createdAt`.

`quickMenus`는 별도 Entity/DB 설정 없이 다음 고정 순서로 반환한다.

| label | url |
|---|---|
| 기관소개 관리 | `/admin/pages` |
| 프로그램 관리 | `/admin/programs` |
| 게시판 관리 | `/admin/boards` |
| 배너 관리 | `/admin/banners` |
| 팝업 관리 | `/admin/popups` |
| 파일 관리 | `/admin/files` |

---

# Page

`pageType`: `GREETING`, `INTRODUCTION`, `HISTORY`, `LOCATION`

`PageResponse`: `id`, `pageType`, `title`, `content`, `createdAt`, `updatedAt`.

`PageRequest`

| field | type | required | Validation |
|---|---|---:|---|
| title | String | Y | `@NotBlank`, max 200 |
| content | String | N | 저장 전 HtmlSanitizer 적용 |

## GET /api/pages/{pageType}

인증: 불필요. 해당 고정 Page 조회.

Response 200: `PageResponse`. Errors: `INVALID_INPUT_VALUE`(400), `PAGE_NOT_FOUND`(404).

## GET /api/admin/pages/{pageType}

인증: ROLE_ADMIN. 공개 API와 별도 관리자 조회 체인을 사용한다.

Response 200: `PageResponse`. Errors: 위 공통 관리자 오류 + `PAGE_NOT_FOUND`(404).

## PUT /api/admin/pages/{pageType}

인증: ROLE_ADMIN. 전체 수정이며 `title`은 필수, `content`는 nullable/optional이다. 동일 `PageRequest`를 사용한다.

Response 200: 수정된 `PageResponse`.

Page는 4개 고정 리소스이며 POST/DELETE를 제공하지 않는다.

---

# Program

`programType`: `COURSE`, `SPECIAL`  
`recruitStatus`: `OPEN`, `CLOSED`

`ProgramRequest`

| field | type | POST required | PUT required | default / Validation |
|---|---|---:|---:|---|
| programType | String(enum) | Y | Y | COURSE/SPECIAL |
| title | String | Y | Y | `@NotBlank`, max 200 |
| content | String | N | N | 저장 전 HtmlSanitizer 적용 |
| thumbnail | String | N | N | max 255, File API가 반환한 URL 문자열 |
| attachment | String | N | N | max 255, File API가 반환한 URL 문자열 |
| googleFormUrl | String | N | N | max 500, 값이 있으면 http/https URL 형식 |
| recruitStatus | String(enum) | N | Y | POST 생략 시 `OPEN`; OPEN/CLOSED |
| isPublic | Boolean | N | Y | POST 생략 시 `false` |

PUT은 리소스 전체 수정 계약이므로 모든 상태 필드(`recruitStatus`, `isPublic`)까지 명시한다. PATCH는 아래 상태 전용 DTO만 사용한다.

`ProgramResponse`: `id`, 위 Program 필드 전체, `createdAt`, `updatedAt`.

## GET /api/programs

인증: 불필요. `isPublic=true`만 반환한다.

Query: 공통 `page`, `size`, `sort` + `programType`(optional enum), `keyword`(optional String; 제목/내용 검색).  
허용 sort: `createdAt`, `title`, `recruitStatus`. 기본 `createdAt,DESC`.

Response 200: `PageResponse<ProgramResponse>`.

## GET /api/programs/{id}

인증: 불필요. `isPublic=true`만 조회 가능하다. 비공개 또는 존재하지 않으면 모두 `PROGRAM_NOT_FOUND`(404).

## GET /api/admin/programs

인증: ROLE_ADMIN. 공개 여부와 관계없이 반환한다.

Query: 공개 목록과 동일(`programType`, `keyword`, `page`, `size`, `sort`). `isPublic=true` 필터를 강제하지 않는다.

Response 200: `PageResponse<ProgramResponse>`.

## GET /api/admin/programs/{id}

인증: ROLE_ADMIN. 비공개 포함 단건 조회. 존재하지 않으면 `PROGRAM_NOT_FOUND`(404).

## POST /api/admin/programs

인증: ROLE_ADMIN. Request: `ProgramRequest`의 POST 규칙.

Response 201: 생성된 `ProgramResponse`.

## PUT /api/admin/programs/{id}

인증: ROLE_ADMIN. Request: 동일 `ProgramRequest`의 PUT 규칙. 부분 수정이 아니므로 누락된 PUT 필수값은 `INVALID_INPUT_VALUE`(400).

Response 200: 수정된 `ProgramResponse`.

## PATCH /api/admin/programs/{id}/visibility

Request `ProgramVisibilityRequest`

```json
{"isPublic": true}
```

`isPublic`: Boolean, required. Response 200: 수정된 `ProgramResponse`.

## PATCH /api/admin/programs/{id}/status

Request `ProgramStatusRequest`

```json
{"recruitStatus": "OPEN"}
```

`recruitStatus`: required enum OPEN/CLOSED. Response 200: 수정된 `ProgramResponse`.

## DELETE /api/admin/programs/{id}

Response 204. 존재하지 않으면 `PROGRAM_NOT_FOUND`(404).

---

# Board

`boardType`: `NOTICE`, `GALLERY`, `ARCHIVE`

`BoardRequest`

| field | type | POST required | PUT required | default / Validation |
|---|---|---:|---:|---|
| boardType | String(enum) | Y | Y | NOTICE/GALLERY/ARCHIVE |
| title | String | Y | Y | `@NotBlank`, max 200 |
| content | String | N | N | 저장 전 HtmlSanitizer 적용 |
| thumbnail | String | N | N | max 255 |
| attachment | String | N | N | max 255 |
| isPublic | Boolean | N | Y | POST 생략 시 `false` |

`viewCount`는 서버 관리 필드이므로 Request에 받지 않는다. 생성 시 0이며 공개 상세 조회 시 TASK.md의 조회수 정책에 따라 증가한다.

`BoardResponse`: `id`, `boardType`, `title`, `content`, `thumbnail`, `attachment`, `viewCount`, `isPublic`, `createdAt`, `updatedAt`.

## GET /api/boards

인증: 불필요. `isPublic=true`만 반환한다.

Query: 공통 `page`, `size`, `sort` + `boardType`(optional), `keyword`(optional; 제목/내용).  
허용 sort: `createdAt`, `title`, `viewCount`. 기본 `createdAt,DESC`.

Response 200: `PageResponse<BoardResponse>`.

## GET /api/boards/{id}

인증: 불필요. 비공개/존재하지 않는 리소스는 `BOARD_NOT_FOUND`(404).

## GET /api/admin/boards

인증: ROLE_ADMIN. 공개 여부와 관계없이 반환한다. Query는 공개 목록과 동일하고 공개 필터를 강제하지 않는다.

## GET /api/admin/boards/{id}

인증: ROLE_ADMIN. 비공개 포함 단건 조회.

## POST /api/admin/boards

Request: `BoardRequest` POST 규칙. Response 201: `BoardResponse`.

## PUT /api/admin/boards/{id}

Request: 동일 `BoardRequest` PUT 규칙. Response 200: `BoardResponse`.

## PATCH /api/admin/boards/{id}/visibility

```json
{"isPublic": true}
```

`isPublic`: Boolean, required. Response 200: `BoardResponse`.

## DELETE /api/admin/boards/{id}

Response 204.

---

# Banner

`BannerRequest`

| field | type | POST required | PUT required | default / Validation |
|---|---|---:|---:|---|
| title | String | Y | Y | `@NotBlank`, max 100 |
| image | String | Y | Y | `@NotBlank`, max 255 |
| linkUrl | String | N | N | max 500; 값이 있으면 http/https URL 형식 |
| sortOrder | Integer | Y | Y | `>= 0`; 공개 메인 캐러셀 노출 순서를 의미하며 값이 작을수록 먼저 노출됨 |
| isVisible | Boolean | N | Y | POST 생략 시 `false` |

`BannerResponse`: `id`, 위 필드 전체, `createdAt`, `updatedAt`.

## GET /api/banners

인증: 불필요. `isVisible=true`만 반환한다. 기본 정렬 `sortOrder,ASC` 후 `createdAt,DESC`. 비페이징 배열 응답이다.

## GET /api/admin/banners

인증: ROLE_ADMIN. 노출 여부와 관계없이 전체 반환. Query `sort` optional, 허용 `sortOrder`, `createdAt`, `title`; 기본 `sortOrder,ASC`. 비페이징 배열 응답.

## GET /api/admin/banners/{id}

인증: ROLE_ADMIN. 비노출 포함 단건 조회.

## POST /api/admin/banners

Request: `BannerRequest` POST 규칙. Response 201: `BannerResponse`.

## PUT /api/admin/banners/{id}

Request: 동일 `BannerRequest` PUT 규칙. Response 200: `BannerResponse`.

## PATCH /api/admin/banners/{id}/visibility

```json
{"isVisible": true}
```

`isVisible`: Boolean, required. Response 200: `BannerResponse`.

## PATCH /api/admin/banners/{id}/order

```json
{"sortOrder": 1}
```

`sortOrder`: Integer, required, `>=0`. Response 200: `BannerResponse`.

## DELETE /api/admin/banners/{id}

Response 204.

---

# Popup

`PopupRequest`

| field | type | POST required | PUT required | default / Validation |
|---|---|---:|---:|---|
| title | String | Y | Y | `@NotBlank`, max 100 |
| content | String | N | N | 저장 전 HtmlSanitizer 적용 |
| startDate | LocalDateTime | Y | Y | ISO-8601 |
| endDate | LocalDateTime | Y | Y | ISO-8601, `startDate <= endDate` |
| isVisible | Boolean | N | Y | POST 생략 시 `false` |

`PopupResponse`: `id`, 위 필드 전체, `createdAt`, `updatedAt`.

## GET /api/popups

인증: 불필요. `isVisible=true`이며 현재 시간이 `startDate <= now <= endDate`인 항목만 반환한다. 기본 `createdAt,DESC`. 비페이징 배열 응답.

## GET /api/admin/popups

인증: ROLE_ADMIN. 노출 여부/기간과 관계없이 전체 반환. Query `sort` optional, 허용 `createdAt`, `startDate`, `endDate`, `title`; 기본 `createdAt,DESC`. 비페이징 배열 응답.

## GET /api/admin/popups/{id}

인증: ROLE_ADMIN. 비노출/기간 외 항목도 조회.

## POST /api/admin/popups

Request: `PopupRequest` POST 규칙. Response 201: `PopupResponse`.

## PUT /api/admin/popups/{id}

Request: 동일 `PopupRequest` PUT 규칙. Response 200: `PopupResponse`.

## PATCH /api/admin/popups/{id}/visibility

```json
{"isVisible": true}
```

`isVisible`: Boolean, required. Response 200: `PopupResponse`.

## DELETE /api/admin/popups/{id}

Response 204.

---

# File

`fileType`: `IMAGE`, `ATTACHMENT`

`FileResponse`: `id`, `originalName`, `url`, `contentType`, `size`, `fileType`, `createdAt`.

## GET /api/admin/files

인증: ROLE_ADMIN. 업로드 이력 관리자 목록.

Query: 공통 `page`, `size`, `sort`. 허용 sort: `createdAt`, `originalName`, `size`, `fileType`; 기본 `createdAt,DESC`.

Response 200: `PageResponse<FileResponse>`.

이 API는 `AdminFileController → FileService → FileRepository → UploadFile` 체인으로 조회하며 P9 `/admin/files` 화면의 데이터 소스다.

## POST /api/admin/files

인증: ROLE_ADMIN. `multipart/form-data`.

| part | type | required | Validation |
|---|---|---:|---|
| file | MultipartFile | Y | empty 금지; fileType에 따른 확장자/크기 검증 |
| fileType | String(enum) | Y | IMAGE/ATTACHMENT |

- IMAGE: jpg/jpeg/png/gif, 최대 5MB, 확장자 검증 후 image magic byte 검증.
- ATTACHMENT: jpg/jpeg/png/gif/pdf/hwp/hwpx/docx/xlsx/pptx/zip, 최대 10MB.

Response 201:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "originalName": "photo.png",
    "url": "/api/files/1",
    "contentType": "image/png",
    "size": 12345,
    "fileType": "IMAGE",
    "createdAt": "2026-08-07T12:00:00"
  },
  "error": null
}
```

Errors: `INVALID_INPUT_VALUE`(400), `INVALID_FILE_TYPE`(400), `FILE_SIZE_EXCEEDED`(400), `FILE_UPLOAD_FAILED`(500).

Program 썸네일/Board 대표이미지/CKEditor 이미지는 `IMAGE`, Program/Board 첨부는 `ATTACHMENT`로 이 단일 엔드포인트를 사용한다. 용도별 별도 업로드 API는 만들지 않는다.

## GET /api/files/{id}

인증: 불필요. 파일 스트림 다운로드/표시. 존재하지 않으면 `FILE_NOT_FOUND`(404).

## DELETE /api/admin/files/{id}

인증: ROLE_ADMIN. UploadFile 레코드와 실제 파일을 함께 삭제한다. 다른 도메인 문자열 URL 참조를 자동 정리하지 않는 ERD.md의 orphan 정책을 유지한다.

Response 204. 존재하지 않으면 `FILE_NOT_FOUND`(404), I/O 실패는 `FILE_UPLOAD_FAILED`(500).

---

# Public / Admin 조회 차이 요약

| Domain | Public GET | Admin GET |
|---|---|---|
| Page | 고정 Page 조회 | 동일 고정 Page, 관리자 전용 체인 |
| Program | `isPublic=true`만 | 공개/비공개 모두 |
| Board | `isPublic=true`만 | 공개/비공개 모두 |
| Banner | `isVisible=true`만 | 노출/비노출 모두 |
| Popup | `isVisible=true` + 노출기간 내 | 노출/비노출/기간 외 모두 |
| File | id 기반 다운로드만 | 업로드 이력 목록 + 업로드/삭제 |

---

# 제외 API

- 회원가입
- 일반 로그인
- 마이페이지
- 상담 신청
- 신청 데이터 저장
- 관리자 계정 등록/수정/목록 API
- Page POST/DELETE

프로그램 신청은 저장하지 않고 `googleFormUrl`로 이동한다.
