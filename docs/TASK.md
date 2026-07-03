# TASK.md

# Development Task

Version 2.0

---

# 프로젝트 목표

교육기관 홈페이지 CMS 구축

- 관리자 CMS
- 프로그램 관리
- 게시판 관리
- Google Form 연동
- 반응형 홈페이지

---

# 개발 순서

## Phase 1. 프로젝트 환경 구성

### 프로젝트 생성

- [ ] Spring Boot 프로젝트 생성
- [ ] Gradle 설정
- [ ] Git Repository 연결
- [ ] application.yml 설정
- [ ] MariaDB 연결

### 라이브러리 추가

- [ ] Spring Security
- [ ] Spring Data JPA
- [ ] QueryDSL
- [ ] Validation
- [ ] Lombok
- [ ] Thymeleaf
- [ ] Bootstrap
- [ ] CKEditor5

---

## Phase 2. 공통 기능

### 공통 클래스

- [ ] BaseEntity
- [ ] ApiResponse
- [ ] ErrorCode
- [ ] GlobalExceptionHandler
- [ ] CommonConfig

### 파일 업로드

- [ ] Local Storage
- [ ] UUID 파일명
- [ ] 날짜별 디렉토리 생성
- [ ] 이미지 업로드
- [ ] 첨부파일 업로드

---

## Phase 3. 관리자 인증

### Spring Security

- [ ] SecurityConfig
- [ ] BCrypt 설정
- [ ] 로그인 구현
- [ ] 로그아웃 구현
- [ ] 인증 실패 처리
- [ ] 접근 권한 설정

### 관리자

- [ ] Admin Entity
- [ ] Admin Repository
- [ ] Admin Service
- [ ] Admin Controller

---

## Phase 4. CMS 페이지 관리

### 기관소개

- [ ] Page Entity
- [ ] Page CRUD
- [ ] CKEditor 적용
- [ ] 이미지 업로드

관리 페이지

- [ ] 인사말
- [ ] 기관소개
- [ ] 연혁
- [ ] 오시는 길

---

## Phase 5. 프로그램 관리

### Program

- [ ] Entity
- [ ] Repository
- [ ] Service
- [ ] Controller

### 기능

- [ ] 프로그램 등록
- [ ] 프로그램 수정
- [ ] 프로그램 삭제
- [ ] 공개 여부
- [ ] 모집 상태
- [ ] Google Form URL 관리
- [ ] 썸네일 업로드
- [ ] 첨부파일 업로드

Program Type

- [ ] COURSE
- [ ] SPECIAL

---

## Phase 6. 게시판 관리

### Board

Board Type

- [ ] NOTICE
- [ ] GALLERY
- [ ] ARCHIVE

### CRUD

- [ ] 목록
- [ ] 상세
- [ ] 등록
- [ ] 수정
- [ ] 삭제

### 기능

- [ ] 검색
- [ ] 페이징
- [ ] 조회수
- [ ] 공개 여부
- [ ] 이미지 업로드
- [ ] 파일 업로드

---

## Phase 7. 메인 관리

### Banner

- [ ] CRUD
- [ ] 이미지 업로드
- [ ] 정렬
- [ ] 공개 여부

### Popup

- [ ] CRUD
- [ ] 기간 설정
- [ ] 공개 여부

---

## Phase 8. 홈페이지

### 메인

- [ ] 메인 화면
- [ ] 배너 표시
- [ ] 팝업 표시
- [ ] 최신 게시글

### 기관소개

- [ ] 페이지 조회

### 프로그램

- [ ] 목록
- [ ] 상세
- [ ] Google Form 이동

### 게시판

- [ ] 공지사항
- [ ] 갤러리
- [ ] 자료실

---

## Phase 9. 관리자 CMS

### Dashboard

- [ ] 최근 게시글
- [ ] 프로그램 현황
- [ ] 빠른 메뉴

### 관리자 화면

- [ ] 프로그램 관리
- [ ] 게시판 관리
- [ ] 페이지 관리
- [ ] 배너 관리
- [ ] 팝업 관리
- [ ] 파일 관리

---

## Phase 10. 테스트

### 기능 테스트

- [ ] 로그인
- [ ] 권한
- [ ] CRUD
- [ ] 검색
- [ ] 파일 업로드
- [ ] Google Form 연결

### 예외 처리

- [ ] Validation
- [ ] 인증 실패
- [ ] 파일 오류
- [ ] 잘못된 요청

---

## Phase 11. UI/UX 개선

- [ ] 반응형 적용
- [ ] 관리자 UI 개선
- [ ] CKEditor 스타일 적용
- [ ] 이미지 최적화
- [ ] 접근성 개선

---

## Phase 12. 배포

### Docker

- [ ] Dockerfile
- [ ] docker-compose

### Nginx

- [ ] Reverse Proxy
- [ ] Static Resource

### GitHub

- [ ] GitHub Actions
- [ ] CI/CD

---

# 완료 기준 (Definition of Done)

## 기능

- [ ] 관리자 로그인 가능
- [ ] 기관소개 CMS 수정 가능
- [ ] 프로그램 관리 가능
- [ ] 게시판 관리 가능
- [ ] 배너 관리 가능
- [ ] 팝업 관리 가능
- [ ] Google Form 정상 연결
- [ ] 파일 업로드 정상 동작

---

## 품질

- [ ] 예외 처리 완료
- [ ] 반응형 적용
- [ ] 권한 검증 완료
- [ ] 코드 리뷰 완료
- [ ] 테스트 완료
- [ ] 배포 완료