# API.md

# REST API Specification

Version 1.0

---

# 공통 규칙

## Base URL

/api

---

## 관리자 URL

/api/admin/**

---

## 인증

관리자 API는 Spring Security 인증이 필요하다.

Public API는 인증 없이 접근 가능하다.

---

# 인증(Authentication)

## 관리자 로그인

POST /api/admin/login

### Request

```json
{
  "loginId": "admin",
  "password": "password"
}
```

### Response

```
200 OK
```

---

## 관리자 로그아웃

POST /api/admin/logout

---

# 대시보드

## 대시보드 조회

GET /api/admin/dashboard

---

# 기관소개(Page)

## 페이지 조회

GET /api/pages/{pageType}

pageType

- greeting
- introduction
- history
- location

---

## 페이지 수정

PUT /api/admin/pages/{pageType}

### Request

```json
{
  "title": "...",
  "content": "..."
}
```

---

# Program

Program은

- 수강 프로그램
- 특강

을 하나의 API로 관리한다.

programType

- COURSE
- SPECIAL

---

## 프로그램 목록

GET /api/programs

### Query

type

keyword

page

size

---

## 프로그램 상세

GET /api/programs/{id}

---

## 프로그램 등록

POST /api/admin/programs

### Request

```json
{
  "programType":"COURSE",
  "title":"...",
  "content":"...",
  "googleFormUrl":"...",
  "recruitStatus":"OPEN",
  "isPublic":true
}
```

---

## 프로그램 수정

PUT /api/admin/programs/{id}

---

## 프로그램 삭제

DELETE /api/admin/programs/{id}

---

## 모집 상태 변경

PATCH /api/admin/programs/{id}/status

### Request

```json
{
  "recruitStatus":"OPEN"
}
```

가능 값

- OPEN
- CLOSED

---

## 공개 여부 변경

PATCH /api/admin/programs/{id}/visibility

### Request

```json
{
  "isPublic":true
}
```

---

# Notice

## 공지사항 목록

GET /api/notices

---

## 공지사항 상세

GET /api/notices/{id}

---

## 공지사항 등록

POST /api/admin/notices

---

## 공지사항 수정

PUT /api/admin/notices/{id}

---

## 공지사항 삭제

DELETE /api/admin/notices/{id}

---

# Gallery

## 목록

GET /api/galleries

---

## 상세

GET /api/galleries/{id}

---

## 등록

POST /api/admin/galleries

---

## 수정

PUT /api/admin/galleries/{id}

---

## 삭제

DELETE /api/admin/galleries/{id}

---

# Archive

## 목록

GET /api/archives

---

## 상세

GET /api/archives/{id}

---

## 등록

POST /api/admin/archives

---

## 수정

PUT /api/admin/archives/{id}

---

## 삭제

DELETE /api/admin/archives/{id}

---

# Banner

## 배너 목록

GET /api/banners

---

## 배너 등록

POST /api/admin/banners

---

## 배너 수정

PUT /api/admin/banners/{id}

---

## 배너 삭제

DELETE /api/admin/banners/{id}

---

## 노출 여부 변경

PATCH /api/admin/banners/{id}/visibility

---

## 정렬 순서 변경

PATCH /api/admin/banners/{id}/order

---

# Popup

## 팝업 목록

GET /api/popups

---

## 팝업 등록

POST /api/admin/popups

---

## 팝업 수정

PUT /api/admin/popups/{id}

---

## 팝업 삭제

DELETE /api/admin/popups/{id}

---

## 노출 여부 변경

PATCH /api/admin/popups/{id}/visibility

---

# File

## 파일 업로드

POST /api/admin/files

multipart/form-data

---

## 파일 삭제

DELETE /api/admin/files/{id}

---

# 검색

## 프로그램 검색

GET /api/programs

Query

- keyword
- type

---

## 공지사항 검색

GET /api/notices

Query

- keyword

---

## 갤러리 검색

GET /api/galleries

Query

- keyword

---

## 자료실 검색

GET /api/archives

Query

- keyword

---

# HTTP Status

200 OK

201 Created

204 No Content

400 Bad Request

401 Unauthorized

403 Forbidden

404 Not Found

500 Internal Server Error

---

# 인증 정책

Public API

- 홈페이지
- 기관소개
- 프로그램
- 공지사항
- 갤러리
- 자료실
- 배너
- 팝업

관리자 API

- 프로그램 관리
- 기관소개 관리
- 게시판 관리
- 배너 관리
- 팝업 관리
- 파일 관리

---

# 제외 API

다음 API는 제공하지 않는다.

- 회원가입
- 일반 로그인
- 마이페이지
- 상담 신청
- 수강 신청 저장
- 특강 신청 저장
- 신청 목록 조회
- 신청 상태 변경

프로그램 신청은 Google Form URL을 통해 외부 Google Form으로 이동한다.