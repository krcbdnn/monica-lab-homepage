# PRD - 모니카 연구소 홈페이지 및 CMS 구축

## 1. 프로젝트 개요

### 프로젝트명

모니카 연구소 홈페이지 및 콘텐츠 관리 시스템(CMS)

### 프로젝트 목적

* 연구소 소개 및 연구 성과 홍보
* 공지사항 및 연구 자료 게시
* 문의 접수 및 관리
* 관리자 페이지를 통한 콘텐츠 운영
* 연구소 담당자가 개발 지식 없이도 콘텐츠를 관리할 수 있는 환경 제공

### 개발 목적

* 실사용 가능한 홈페이지 구축
* Spring Boot 기반 실무형 아키텍처 적용
* 인증/인가, 파일 업로드, 검색, 캐싱, CI/CD 경험 확보
* 백엔드 포트폴리오 및 취업 역량 강화

---

## 2. 기술 스택

### Backend

* Java 21
* Spring Boot 3
* Spring Security
* JWT Authentication
* JPA (Hibernate)
* QueryDSL
* Validation

### Frontend

* Thymeleaf
* Tailwind CSS
* Alpine.js

### Database

* MySQL
* Redis

### Infrastructure

* AWS EC2
* AWS S3
* Nginx
* Docker
* Docker Compose

### DevOps

* GitHub
* GitHub Actions
* Jenkins

---

## 3. 사용자 기능

### 메인 페이지

* 연구소 소개
* 대표 이미지 배너
* 최신 공지사항
* 최신 연구자료
* 최신 뉴스

### 연구소 소개

* 인사말
* 비전
* 조직도
* 연혁

### 연구 분야

* 연구 분야 목록
* 연구 분야 상세 소개

### 공지사항

* 목록 조회
* 상세 조회
* 검색
* 첨부파일 다운로드

### 연구 자료실

* 목록 조회
* 상세 조회
* 파일 다운로드
* 검색

### 뉴스

* 목록 조회
* 상세 조회

### 문의하기

* 문의 등록
* 이메일 알림 발송
* 개인정보 수집 동의

---

## 4. 관리자 기능

### 관리자 인증

* 로그인
* JWT 기반 인증
* 권한 검증

### 공지사항 관리

* 등록
* 수정
* 삭제
* 검색

### 연구자료 관리

* 등록
* 수정
* 삭제
* 파일 업로드

### 뉴스 관리

* 등록
* 수정
* 삭제

### 문의 관리

* 문의 목록 조회
* 문의 상태 변경
* 답변 관리

### 배너 관리

* 메인 배너 등록
* 수정
* 삭제
* 노출 순서 변경

### 관리자 로그

* 로그인 기록
* 게시글 등록 기록
* 수정 기록
* 삭제 기록

---

## 5. 파일 관리

### 업로드 대상

* 이미지
* PDF
* 문서 파일

### 저장 방식

* AWS S3 저장
* UUID 기반 파일명 생성

### 기능

* 업로드
* 다운로드
* 삭제
* 미리보기

---

## 6. 검색 기능

### QueryDSL 적용

검색 조건

* 제목
* 내용
* 작성일
* 카테고리

대상

* 공지사항
* 연구자료
* 뉴스

---

## 7. 성능 최적화

### Redis

* 인기 게시글 캐싱
* 조회수 캐싱
* 세션 데이터 저장

### Database

* N+1 문제 방지
* Fetch Join 적용
* 인덱스 최적화

---

## 8. 보안

### Spring Security

* 인증 및 인가

### JWT

* Access Token
* Refresh Token

### 기타

* CSRF 대응
* XSS 방지
* 입력값 검증
* 파일 업로드 검증

---

## 9. 모니터링

### 로그

* Application Log
* Access Log
* Error Log

### 관리자 감사 로그

* 사용자
* 작업 종류
* 작업 시간

---

## 10. 배포

### 개발 환경

Local Docker Environment

### 운영 환경

EC2

### Reverse Proxy

Nginx

### CI/CD

GitHub Push

↓

GitHub Actions

↓

Jenkins Build

↓

Docker Image Build

↓

EC2 배포

---

## 11. 향후 확장 기능

### 1차 확장

* 관리자 다중 권한
* 통계 대시보드
* 방문자 분석

### 2차 확장

* 연구과제 관리
* 연구원 프로필 관리
* 연구 성과 관리

### 3차 확장

* AI 챗봇
* 다국어 지원
* 외부 API 연동
