# API.md

## Base URL

```
/api
```

---

# Authentication

## 로그인

### POST /api/auth/login

### Request

```json
{
  "email": "admin@test.com",
  "password": "1234"
}
```

### Response

```json
{
  "accessToken": "jwt-token",
  "refreshToken": "refresh-token",
  "role": "ADMIN"
}
```

---

## 로그아웃

### POST /api/auth/logout

Header

```
Authorization: Bearer {accessToken}
```

Response

```
204 No Content
```

---

# Board

## 게시글 목록

### GET /api/posts

Query

```
?page=0
&size=10
&category=NOTICE
&keyword=spring
```

Response

```json
{
  "content": [
    {
      "id": 1,
      "title": "공지사항",
      "writer": "관리자",
      "createdAt": "2026-06-17",
      "viewCount": 12
    }
  ],
  "page": 0,
  "totalPages": 3
}
```

---

## 게시글 상세

### GET /api/posts/{id}

Response

```json
{
  "id": 1,
  "title": "공지사항",
  "content": "...",
  "writer": "관리자",
  "attachments": [
    {
      "fileName": "guide.pdf",
      "url": "/upload/guide.pdf"
    }
  ]
}
```

---

## 게시글 등록

### POST /api/posts

Content-Type

```
multipart/form-data
```

Request

```
title
content
category
files[]
```

Response

```json
{
  "id": 12
}
```

---

## 게시글 수정

### PUT /api/posts/{id}

Content-Type

```
multipart/form-data
```

Request

```
title
content
category
files[]
deleteFileIds[]
```

Response

```
200 OK
```

---

## 게시글 삭제

### DELETE /api/posts/{id}

Response

```
204 No Content
```

---

# Consultation

## 상담 신청

### POST /api/consultations

Request

```json
{
  "name": "홍길동",
  "phone": "01012341234",
  "email": "hong@test.com",
  "message": "문의드립니다."
}
```

Response

```json
{
  "id": 3,
  "status": "WAITING"
}
```

---

## 상담 목록 (관리자)

### GET /api/admin/consultations

Query

```
?page=0
&size=20
&status=WAITING
```

Response

```json
{
  "content": [
    {
      "id": 3,
      "name": "홍길동",
      "phone": "01012341234",
      "status": "WAITING",
      "createdAt": "2026-06-17"
    }
  ]
}
```

---

## 상담 상태 변경

### PATCH /api/admin/consultations/{id}

Request

```json
{
  "status": "DONE"
}
```

Response

```
200 OK
```

---

# Admin Dashboard

## 대시보드

### GET /api/admin/dashboard

Response

```json
{
  "todayConsultation": 3,
  "totalConsultation": 153,
  "postCount": 52
}
```

---

# Error Response

모든 API는 동일한 에러 형식을 사용한다.

```json
{
  "timestamp": "2026-06-17T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation Failed",
  "path": "/api/posts"
}
```
