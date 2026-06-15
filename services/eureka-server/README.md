# Eureka Server

## 개요
MSA 환경에서 서비스 디스커버리를 담당하는 Eureka Registry Server입니다.
모든 마이크로서비스는 기동 시 이 서버에 자신을 등록하고, 다른 서비스의 위치를 조회합니다.

## 포트
- `8761`

## 주요 기능
- 서비스 인스턴스 등록 및 해제
- 서비스 헬스체크 (Heartbeat)
- 서비스 인스턴스 목록 제공 (Registry)

## Eureka Dashboard
서버 실행 후 `http://localhost:8761` 에서 등록된 서비스 목록을 확인할 수 있습니다.

## 실행 방법
```bash
./gradlew :eureka-server:bootRun
```
