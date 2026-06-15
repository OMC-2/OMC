# Product Service

## 개요
상품 정보 관리 및 재고 관리를 담당하는 마이크로서비스입니다.
Redis를 활용한 재고 캐싱으로 동시성 제어를 처리합니다.

## 포트
- `8082`

## 주요 기능
- 상품 등록/수정/삭제/조회 (`/api/products`)
- 재고 조회 및 차감
- Redis 기반 재고 캐싱 (동시성 처리)

## 기술 스택
- Spring Boot 3.2.x
- Spring Data JPA
- Spring Data Redis
- MySQL
- Eureka Client

## DB 테이블
- `products`: 상품 정보 (id, name, description, price, stock, status)
