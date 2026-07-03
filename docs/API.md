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

---

# Dashboard

GET /api/admin/dashboard

---

# Page

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

---

# Program

programType

- COURSE
- SPECIAL

---

## 목록

GET /api/programs

Query

- type
- keyword
- page
- size

---

## 상세

GET /api/programs/{id}

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

- type
- keyword
- page
- size

예)

GET /api/boards?type=NOTICE

GET /api/boards?type=GALLERY

GET /api/boards?type=ARCHIVE

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

- 메인
- 기관소개
- 프로그램
- 게시판
- 배너
- 팝업

---

# Admin API

- 관리자 로그인
- 페이지 관리
- 프로그램 관리
- 게시판 관리
- 배너 관리
- 팝업 관리
- 파일 관리

---

# 제외 API

제공하지 않는 기능

- 회원가입
- 일반 로그인
- 마이페이지
- 상담 신청
- 수강 신청 저장
- 특강 신청 저장
- 신청 관리

프로그램 신청은 Google Form URL로 이동한다.