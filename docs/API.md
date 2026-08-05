# API.md

# REST API Specification

Version 2.0

---

# Base URL

/api

---

# 인증

관리자 API만 인증이 필요하다.

관리자 URL

```
/api/admin/**
```

---

# Authentication

## 로그인

POST /api/admin/login

## 로그아웃

POST /api/admin/logout

## 로그인 관리자 정보 조회

GET /api/admin/me

세션으로 인증된 관리자 본인의 정보(`id`, `loginId`, `name`, `role`)를 조회한다. 관리자 공통 레이아웃(P9-T2a)의 헤더에 로그인한 관리자명을 표시하는 용도로 사용하며, ARCHITECTURE.md `AdminController`("관리자 계정 조회 등 내부 용도")가 담당하는 유일한 API다. 다른 관리자 계정을 조회/등록/수정하는 API는 제공하지 않는다(관리자 계정 관리 API는 본 프로젝트 범위 밖).

Response

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

---

# Dashboard

GET /api/admin/dashboard

---

# Page

## 페이지 조회

GET /api/pages/{pageType}

pageType

- GREETING
- INTRODUCTION
- HISTORY
- LOCATION

---

## 페이지 수정

PUT /api/admin/pages/{pageType}

---

# Program

programType

- COURSE
- SPECIAL

---

## 목록

GET /api/programs

Query

- programType
- keyword
- page
- size

---

## 등록

POST /api/admin/programs

---

## 수정

PUT /api/admin/programs/{id}

---

## 삭제

DELETE /api/admin/programs/{id}

---

## 공개 여부

PATCH /api/admin/programs/{id}/visibility

---

## 모집 상태

PATCH /api/admin/programs/{id}/status

---

# Board

boardType

- NOTICE
- GALLERY
- ARCHIVE

---

## 목록

GET /api/boards

Query

- boardType
- keyword
- page
- size

예)

GET /api/boards?boardType=NOTICE

GET /api/boards?boardType=GALLERY

GET /api/boards?boardType=ARCHIVE

---

## 상세

GET /api/boards/{id}

---

## 등록

POST /api/admin/boards

---

## 수정

PUT /api/admin/boards/{id}

---

## 삭제

DELETE /api/admin/boards/{id}

---

## 공개 여부

PATCH /api/admin/boards/{id}/visibility

---

# Banner

## 목록

GET /api/banners

---

## 등록

POST /api/admin/banners

---

## 수정

PUT /api/admin/banners/{id}

---

## 삭제

DELETE /api/admin/banners/{id}

---

## 노출 여부

PATCH /api/admin/banners/{id}/visibility

---

## 정렬

PATCH /api/admin/banners/{id}/order

---

# Popup

## 목록

GET /api/popups

---

## 등록

POST /api/admin/popups

---

## 수정

PUT /api/admin/popups/{id}

---

## 삭제

DELETE /api/admin/popups/{id}

---

## 노출 여부

PATCH /api/admin/popups/{id}/visibility

---

# File

## 업로드

POST /api/admin/files

multipart/form-data

Request Part

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| file | File | Y | 업로드할 파일 본문 |
| fileType | String (Form field) | Y | `IMAGE` 또는 `ATTACHMENT`(ERD.md File.file_type 기준). 서버는 이 값에 따라 CODING_RULES.md의 `ALLOWED_IMAGE_EXTENSIONS`/`ALLOWED_ATTACHMENT_EXTENSIONS`와 `MAX_IMAGE_SIZE`/`MAX_UPLOAD_SIZE` 중 해당 기준을 선택하여 검증한다. |

Response

```json
{
  "success": true,
  "data": {
    "id": 1,
    "url": "/api/files/1",
    "originalName": "photo.png"
  },
  "error": null
}
```

- Program 썸네일(`fileType=IMAGE`), Program/Board 첨부파일(`fileType=ATTACHMENT`), CKEditor 삽입 이미지(`fileType=IMAGE`) 모두 이 단일 엔드포인트를 `fileType`으로 구분하여 사용한다. 용도별 별도 엔드포인트를 두지 않는다.

---

## 다운로드

GET /api/files/{id}

---

## 삭제

DELETE /api/admin/files/{id}

---

# HTTP Status

- 200 OK
- 201 Created
- 204 No Content
- 400 Bad Request
- 401 Unauthorized
- 403 Forbidden
- 404 Not Found
- 500 Internal Server Error

---

# Public API

인증 없이 호출 가능한 API이다.

대상 도메인은 위 Page, Program, Board, Banner, Popup, File(다운로드 `GET /api/files/{id}`)의 관리자 접두사(/admin)가 없는 API를 참고한다.

---

# Admin API

`/api/admin/**` 인증이 필요한 API이다.

대상 도메인은 위 Authentication, Dashboard, Page(수정), Program(등록/수정/삭제/공개여부/모집상태), Board(등록/수정/삭제/공개여부), Banner, Popup, File을 참고한다.

---

# 제외 API

제공하지 않는 기능

- 회원가입
- 일반 로그인
- 마이페이지
- 상담 신청
- 신청 데이터 저장

프로그램 신청은 Google Form URL로 이동한다.