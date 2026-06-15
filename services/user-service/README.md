# User Service

## 개요
회원 관리를 담당하는 마이크로서비스입니다.
회원가입, 로그인, JWT 토큰 발급/검증, Spring Security 기반 인증을 처리합니다.

## 포트
- `8081`

## 주요 기능
- 회원가입 (`POST /api/users/signup`)
- 로그인 (`POST /api/users/login`)
- 내 정보 조회 (`GET /api/users/me`)
- JWT Access Token / Refresh Token 발급
- Spring Security 기반 인증/인가

## 기술 스택
- Spring Boot 3.2.x
- Spring Security 6.x
- Spring Data JPA
- jjwt 0.12.x
- MySQL
- Eureka Client

## DB 테이블
- `users`: 회원 정보 (id, email, password, nickname, role, created_at)
