#!/bin/bash
# E2E 테스트 실행 스크립트
#
# ================================================================
# 사용법
# ================================================================
#
#   bash e2e/run.sh                   # 전체 실행
#   bash e2e/run.sh [대상]             # 특정 대상만 실행
#
# ----------------------------------------------------------------
# 대상 목록
# ----------------------------------------------------------------
#
#   [서비스 그룹]
#   user                → user/ 폴더 전체 (회원가입 + 로그인 + 프로필)
#   coupon              → coupon/ 폴더 전체 (쿠폰 생성 + 발급 + 조회 + 보안)
#   notification        → notification/ 폴더 전체 (알림 목록 + 읽음 처리 + 보안)
#   drop                → drop/ 폴더 전체 (드롭 Admin CRUD + 조회 + 구매 선점)
#   saga                → saga/ 폴더 전체 (서비스 연계 시나리오)
#
#   [개별 시나리오]
#   user/signup              → 회원가입 (정상 201 / 중복 409 / 어드민 201)
#   user/login               → 로그인 (정상 200 + 토큰 발급 / 잘못된 비밀번호 401)
#   user/profile             → 프로필 조회 (정상 200 / 토큰 없음 401)
#   user/token_refresh       → 토큰 갱신 (정상 200 / 잘못된 토큰 401)
#   user/profile_update      → 프로필 수정 (정상 200 / 탈퇴 200 / 미인증 401)
#   user/address             → 주소 CRUD + 기본 주소 설정
#   user/security            → 인증·인가·경로 보안 검증
#   coupon/coupon_create     → 쿠폰 생성 (정상 201 / 필드누락 400 / 권한오류 403·401)
#   coupon/coupon_issue      → 쿠폰 발급 (정상 201 / 중복 409 / 품절 409 / 권한오류 403·401)
#   coupon/coupon_my         → 내 쿠폰 목록·단건 조회 (정상 200 / 없는ID 404 / 미인증 401)
#   coupon/coupon_security   → 쿠폰 인증·인가 보안 검증
#   notification/notification_list     → 알림 목록 조회 (빈 목록 / 쿠폰발급 후 조회 / 인증오류)
#   notification/notification_read     → 읽음 처리 (본인/타인/미존재 / 인증오류)
#   notification/notification_security → 알림 인증·인가 보안 검증
#   drop/drop_admin_crud     → 드롭 Admin CRUD (생성 201 / 권한 403·401 / 수정 200 / 삭제 204)
#   drop/drop_query          → 드롭 조회 (목록 200 / status 필터 / 단건 200 / 없는ID 404)
#   drop/drop_purchase       → 구매 선점 (정상 202 / 중복 409 / 재고소진 409 / 미오픈 409 / 미인증 401)
#
# ----------------------------------------------------------------
# 예시
# ----------------------------------------------------------------
#
#   bash e2e/run.sh                          # 모든 시나리오 실행
#   bash e2e/run.sh user                     # user 서비스 시나리오 전체
#   bash e2e/run.sh coupon                   # coupon 서비스 시나리오 전체
#   bash e2e/run.sh notification             # notification 서비스 시나리오 전체
#   bash e2e/run.sh drop                     # drop 서비스 시나리오 전체
#   bash e2e/run.sh user/signup              # 회원가입 시나리오만
#   bash e2e/run.sh user/login               # 로그인 시나리오만
#   bash e2e/run.sh user/profile             # 프로필 조회 시나리오만
#   bash e2e/run.sh user/token_refresh       # 토큰 갱신 시나리오만
#   bash e2e/run.sh user/profile_update      # 프로필 수정 시나리오만
#   bash e2e/run.sh user/address             # 주소 관리 시나리오만
#   bash e2e/run.sh user/security            # user 보안 시나리오만
#   bash e2e/run.sh coupon/coupon_create     # 쿠폰 생성 시나리오만
#   bash e2e/run.sh coupon/coupon_issue      # 쿠폰 발급 시나리오만
#   bash e2e/run.sh coupon/coupon_my         # 내 쿠폰 조회 시나리오만
#   bash e2e/run.sh coupon/coupon_security   # coupon 보안 시나리오만
#   bash e2e/run.sh notification/notification_list     # 알림 목록 조회 시나리오만
#   bash e2e/run.sh notification/notification_read     # 읽음 처리 시나리오만
#   bash e2e/run.sh notification/notification_security # 알림 보안 시나리오만
#   bash e2e/run.sh drop/drop_admin_crud     # 드롭 Admin CRUD 시나리오만
#   bash e2e/run.sh drop/drop_query          # 드롭 조회 시나리오만
#   bash e2e/run.sh drop/drop_purchase       # 드롭 구매 선점 시나리오만
#
GATEWAY_SECRET=local-secret
ADMIN_SECRET=local-admin-secret
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

TARGET=$1

if [ -n "$TARGET" ]; then
  # 슬래시가 포함된 경우 개별 feature 파일 → .feature 확장자 추가
  # 슬래시가 없는 경우 폴더 전체 → 확장자 불필요
  if [[ "$TARGET" == */* ]]; then
    KARATE_OPTS="classpath:${TARGET}.feature"
  else
    KARATE_OPTS="classpath:$TARGET"
  fi
else
  KARATE_OPTS=""
fi

# 유효한 대상인지 확인
VALID_TARGETS="user coupon notification drop saga user/signup user/login user/profile user/token_refresh user/profile_update user/address user/security coupon/coupon_create coupon/coupon_issue coupon/coupon_my coupon/coupon_security notification/notification_list notification/notification_read notification/notification_security drop/drop_admin_crud drop/drop_query drop/drop_purchase"
if [ -n "$TARGET" ]; then
  VALID=false
  for t in $VALID_TARGETS; do
    if [ "$TARGET" = "$t" ]; then
      VALID=true
      break
    fi
  done

  if [ "$VALID" = false ]; then
    echo "알 수 없는 대상: $TARGET"
    echo ""
    echo "사용 가능한 대상:"
    echo "  [서비스 그룹]"
    echo "  user              → user/ 폴더 전체"
    echo "  coupon            → coupon/ 폴더 전체"
    echo "  notification      → notification/ 폴더 전체"
    echo "  saga              → saga/ 폴더 전체"
    echo ""
    echo "  [개별 시나리오 — user]"
    echo "  user/signup              → 회원가입"
    echo "  user/login               → 로그인"
    echo "  user/profile             → 프로필 조회"
    echo "  user/token_refresh       → 토큰 갱신"
    echo "  user/profile_update      → 프로필 수정 및 탈퇴"
    echo "  user/address             → 주소 CRUD + 기본 주소 설정"
    echo "  user/security            → 인증·인가·경로 보안 검증"
    echo ""
    echo "  [개별 시나리오 — coupon]"
    echo "  coupon/coupon_create     → 쿠폰 생성"
    echo "  coupon/coupon_issue      → 쿠폰 발급"
    echo "  coupon/coupon_my         → 내 쿠폰 조회"
    echo "  coupon/coupon_security   → 쿠폰 인증·인가 보안 검증"
    echo ""
    echo "  [개별 시나리오 — notification]"
    echo "  notification/notification_list     → 알림 목록 조회"
    echo "  notification/notification_read     → 읽음 처리"
    echo "  notification/notification_security → 알림 인증·인가 보안 검증"
    echo ""
    echo "  [개별 시나리오 — drop]"
    echo "  drop/drop_admin_crud     → 드롭 Admin CRUD"
    echo "  drop/drop_query          → 드롭 조회"
    echo "  drop/drop_purchase       → 드롭 구매 선점"
    exit 1
  fi
fi

JAVA_HOME=$JAVA_HOME \
GATEWAY_SECRET=$GATEWAY_SECRET \
ADMIN_SECRET=$ADMIN_SECRET \
./gradlew :e2e:test -PrunE2E \
  $([ -n "$KARATE_OPTS" ] && echo "-Dkarate.options=$KARATE_OPTS")
