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
#   payment             → payment/ 폴더 전체 (결제 승인 + 조회 + 취소 + 보안)
#   saga                → saga/ 폴더 전체 (서비스 연계 시나리오)
#   scenario            → scenario/ 폴더 전체 (핵심 시연 시나리오)
#   [시연 시나리오 그룹]
#   scenario/03_coupon_concurrency  → 쿠폰 동시 발급 시나리오 전체
#   scenario/06_auth_errors         → 권한 오류 시나리오 전체
#
#   [시연 시나리오 개별]
#   scenario/01_drop_purchase/01_normal                  → 드롭 정상 구매 해피패쓰
#   scenario/03_coupon_concurrency/01_concurrent_issue   → 동시 발급 → 수량만 성공
#   scenario/03_coupon_concurrency/02_duplicate_issue    → 중복 발급 차단
#   scenario/06_auth_errors/00_signup_happy              → 회원가입 해피패쓰
#   scenario/06_auth_errors/01_unauthenticated           → 비로그인 접근 차단
#   scenario/06_auth_errors/02_unauthorized              → 권한 오류
#   scenario/06_auth_errors/03_token_refresh             → 토큰 갱신
#
#   [개별 시나리오]
#   user/signup              → 회원가입 (정상 201 / 중복 409 / 어드민 201)
#   user/login               → 로그인 (정상 200 + 토큰 발급 / 잘못된 비밀번호 401)
#   user/profile             → 프로필 조회 (정상 200 / 토큰 없음 401)
#   user/token_refresh       → 토큰 갱신 (정상 200 / 잘못된 토큰 401)
#   user/profile_update      → 프로필 수정 (정상 200 / 탈퇴 200 / 미인증 401)
#   user/address             → 주소 CRUD + 기본 주소 설정
#   user/security            → 인증·인가·경로 보안 검증
#   coupon/coupon_create      → 쿠폰 생성 (정상 201 / 필드누락 400 / 권한오류 403·401)
#   coupon/coupon_issue       → 쿠폰 발급 (정상 201 / 중복 409 / 품절 409 / 권한오류 403·401)
#   coupon/coupon_my          → 내 쿠폰 목록·단건 조회 (정상 200 / 없는ID 404 / 미인증 401)
#   coupon/coupon_security    → 쿠폰 인증·인가 보안 검증
#   coupon/coupon_rate_limit  → 쿠폰 발급 Rate Limiting 검증 (Gateway burstCapacity=10)
#   coupon/coupon_cold_start  → 쿠폰 발급 Cold Start 검증 (첫 호출·두 번째 호출 연속 201)
#   notification/notification_list     → 알림 목록 조회 (빈 목록 / 쿠폰발급 후 조회 / 인증오류)
#   notification/notification_read     → 읽음 처리 (본인/타인/미존재 / 인증오류)
#   notification/notification_security → 알림 인증·인가 보안 검증
#   drop/drop_admin_crud     → 드롭 Admin CRUD (생성 201 / 권한 403·401 / 수정 200 / 삭제 204)
#   drop/drop_query          → 드롭 조회 (목록 200 / status 필터 / 단건 200 / 없는ID 404)
#   drop/drop_purchase       → 구매 선점 (정상 202 / 중복 409 / 재고소진 409 / 미오픈 409 / 미인증 401)
#   payment/payment_flow                → 결제 승인·조회·취소
#   payment/payment_security            → 결제 인증·인가 보안
#
#   [통합 시나리오]
#   scenario/01_drop_purchase/01_normal                 → 쿠폰 적용 드롭 정상 구매
#   scenario/01_drop_purchase/02_refund                 → 환불 요청 → 결제 취소 → 환불 알림
#   scenario/02_raffle_purchase/01_entry_with_coupon    → 응모 (쿠폰 적용 + 선결제)
#   scenario/02_raffle_purchase/02_draw_and_notify      → 추첨 → 당첨 알림 / 낙첨 자동 환불 + 알림
#   scenario/03_coupon_concurrency/01_concurrent_issue  → 수량 초과 동시 발급 → 정확히 수량만 성공
#   scenario/03_coupon_concurrency/02_duplicate_issue   → 중복 발급 시도 → 실패
#   scenario/04_payment_saga/01_payment_failure       → 결제 수단 오류 보상
#   scenario/04_payment_saga/02_stock_failure         → 재고 차감 실패 결제 취소
#   scenario/04_payment_saga/03_hold_expire           → 구매 hold 만료
#   scenario/04_payment_saga/04_idempotency           → PG 오류 결제 멱등성
#   scenario/05_admin/01_drop_modify            → 오픈 전 드롭 수정 / 삭제
#   scenario/05_admin/02_raffle_modify          → 오픈 전 래플 수정 / 삭제
#   scenario/05_admin/03_raffle_status          → 래플 상태 강제 변경 (SCHEDULED → CLOSED)
#   scenario/05_admin/04_raffle_draw            → 응모자 목록 조회 + 수동 추첨
#   scenario/06_auth_errors/01_unauthenticated  → 비로그인 구매 시도 → 인증 오류
#   scenario/06_auth_errors/02_unauthorized     → 일반 유저 관리자 API 호출 → 권한 오류
#   scenario/06_auth_errors/03_token_refresh    → 만료 토큰 → 리프레시 → 새 토큰 발급
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
#   bash e2e/run.sh payment                  # payment 서비스 시나리오 전체
#   bash e2e/run.sh user/signup              # 회원가입 시나리오만
#   bash e2e/run.sh user/login               # 로그인 시나리오만
#   bash e2e/run.sh user/profile             # 프로필 조회 시나리오만
#   bash e2e/run.sh user/token_refresh       # 토큰 갱신 시나리오만
#   bash e2e/run.sh user/profile_update      # 프로필 수정 시나리오만
#   bash e2e/run.sh user/address             # 주소 관리 시나리오만
#   bash e2e/run.sh user/security            # user 보안 시나리오만
#   bash e2e/run.sh coupon/coupon_create      # 쿠폰 생성 시나리오만
#   bash e2e/run.sh coupon/coupon_issue       # 쿠폰 발급 시나리오만
#   bash e2e/run.sh coupon/coupon_my          # 내 쿠폰 조회 시나리오만
#   bash e2e/run.sh coupon/coupon_security    # coupon 보안 시나리오만
#   bash e2e/run.sh coupon/coupon_rate_limit  # 쿠폰 발급 Rate Limiting 검증
#   bash e2e/run.sh coupon/coupon_cold_start  # 쿠폰 발급 Cold Start 검증
#   bash e2e/run.sh notification/notification_list     # 알림 목록 조회 시나리오만
#   bash e2e/run.sh notification/notification_read     # 읽음 처리 시나리오만
#   bash e2e/run.sh notification/notification_security # 알림 보안 시나리오만
#   bash e2e/run.sh drop/drop_admin_crud     # 드롭 Admin CRUD 시나리오만
#   bash e2e/run.sh drop/drop_query          # 드롭 조회 시나리오만
#   bash e2e/run.sh drop/drop_purchase       # 드롭 구매 선점 시나리오만
#   bash e2e/run.sh payment/payment_flow                # 결제 승인·조회·취소
#   bash e2e/run.sh payment/payment_security            # 결제 보안 시나리오만
#   bash e2e/run.sh scenario                                            # 핵심 시연 시나리오 전체
#   bash e2e/run.sh scenario/01_drop_purchase                           # 드롭 시나리오 전체
#   bash e2e/run.sh scenario/02_raffle_purchase                         # 래플 시나리오 전체
#   bash e2e/run.sh scenario/03_coupon_concurrency                      # 쿠폰 동시 발급 시나리오 전체
#   bash e2e/run.sh scenario/04_payment_saga                            # 결제 실패 시나리오 전체
#   bash e2e/run.sh scenario/05_admin                                   # 관리자 운영 시나리오 전체
#   bash e2e/run.sh scenario/06_auth_errors                             # 권한 오류 시나리오 전체
#   bash e2e/run.sh scenario/01_drop_purchase/01_normal                 # 드롭 정상 구매 해피패쓰
#   bash e2e/run.sh scenario/01_drop_purchase/02_refund                 # 환불 요청 → 결제 취소 → 환불 알림
#   bash e2e/run.sh scenario/02_raffle_purchase/01_entry_with_coupon    # 응모 (쿠폰 적용 + 선결제)
#   bash e2e/run.sh scenario/02_raffle_purchase/02_duplicate_issue      # 중복 발급 시도 → 실패
#   bash e2e/run.sh scenario/03_coupon_concurrency/01_concurrent_issue  # 동시 발급
#   bash e2e/run.sh scenario/03_coupon_concurrency/02_duplicate_issue   # 중복 발급 차단
#   bash e2e/run.sh scenario/04_payment_saga/01_payment_failure         # 결제 수단 오류 → 재고/쿠폰 롤백 + 실패 알림
#   bash e2e/run.sh scenario/04_payment_saga/02_stock_failure           # 재고 차감 실패 → 결제 승인 취소 보상
#   bash e2e/run.sh scenario/04_payment_saga/03_hold_expire             # 구매 hold 만료 → 선점 자동 해제
#   bash e2e/run.sh scenario/04_payment_saga/04_idempotency             # 외부 결제 오류 → 멱등성 키로 중복 결제 방지
#   bash e2e/run.sh scenario/05_admin/01_drop_modify                    # 오픈 전 드롭 수정/삭제
#   bash e2e/run.sh scenario/05_admin/02_raffle_modify                  # 오픈 전 래플 수정/삭제
#   bash e2e/run.sh scenario/05_admin/03_raffle_status                  # 래플 상태 강제 변경 (SCHEDULED→OPEN→CLOSED)
#   bash e2e/run.sh scenario/05_admin/04_raffle_draw                    # 응모자 목록 조회 + 수동 추첨
#   bash e2e/run.sh scenario/06_auth_errors/00_signup_happy             # 회원가입 해피패쓰
#   bash e2e/run.sh scenario/06_auth_errors/01_unauthenticated          # 비로그인 접근 차단
#   bash e2e/run.sh scenario/06_auth_errors/02_unauthorized             # 권한 오류
#   bash e2e/run.sh scenario/06_auth_errors/03_token_refresh            # 토큰 갱신
#
GATEWAY_SECRET=local-secret
ADMIN_SECRET=local-admin-secret
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null)}

TARGET=$1
RESOURCES_DIR="e2e/src/test/resources"

if [ -n "$TARGET" ]; then
  # 1. 사용자가 이미 파일 확장자(.feature)를 붙여서 입력한 경우 그대로 사용
  if [[ "$TARGET" == *.feature ]]; then
    KARATE_OPTS="classpath:${TARGET}"
  # 2. 실제 폴더 경로인 경우 폴더 전체를 실행
  elif [ -d "e2e/src/test/resources/$TARGET" ]; then
    KARATE_OPTS="classpath:${TARGET}"
  # 3. 그 외에 슬래시가 포함된 경우 개별 feature 파일로 간주하여 .feature 추가
  elif [[ "$TARGET" == */* ]]; then
    KARATE_OPTS="classpath:${TARGET}.feature"
  # 4. 슬래시가 없는 경우 폴더 전체로 간주
  else
    KARATE_OPTS="classpath:$TARGET"
  fi
else
  KARATE_OPTS=""
fi

# 유효한 대상인지 확인
VALID_TARGETS="user coupon notification drop payment saga scenario user/signup user/login user/profile user/token_refresh user/profile_update user/address user/security coupon/coupon_create coupon/coupon_issue coupon/coupon_issue_happy coupon/coupon_cold_start coupon/coupon_my coupon/coupon_security coupon/coupon_rate_limit notification/notification_list notification/notification_read notification/notification_security drop/drop_admin_crud drop/drop_query drop/drop_purchase payment/payment_flow payment/payment_security scenario/01_drop_purchase scenario/01_drop_purchase/01_normal scenario/03_coupon_concurrency scenario/03_coupon_concurrency/01_concurrent_issue scenario/03_coupon_concurrency/02_duplicate_issue scenario/04_payment_saga scenario/04_payment_saga/01_payment_failure scenario/04_payment_saga/02_stock_failure scenario/04_payment_saga/03_hold_expire scenario/04_payment_saga/04_idempotency scenario/05_admin scenario/05_admin/01_drop_modify scenario/05_admin/02_raffle_modify scenario/05_admin/03_raffle_status scenario/05_admin/04_raffle_draw scenario/06_auth_errors scenario/06_auth_errors/00_signup_happy scenario/06_auth_errors/01_unauthenticated scenario/06_auth_errors/02_unauthorized scenario/06_auth_errors/03_token_refresh"
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
    echo "  payment           → payment/ 폴더 전체"
    echo "  saga              → saga/ 폴더 전체"
    echo "  scenario          → scenario/ 폴더 전체"
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
    echo "  coupon/coupon_create      → 쿠폰 생성"
    echo "  coupon/coupon_issue       → 쿠폰 발급"
    echo "  coupon/coupon_my          → 내 쿠폰 조회"
    echo "  coupon/coupon_security    → 쿠폰 인증·인가 보안 검증"
    echo "  coupon/coupon_rate_limit  → 쿠폰 발급 Rate Limiting 검증"
    echo "  coupon/coupon_cold_start  → 쿠폰 발급 Cold Start 검증"
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
    echo ""
    echo "  [개별 시나리오 — payment]"
    echo "  payment/payment_flow     → 결제 승인·조회·취소"
    echo "  payment/payment_security → 결제 인증·인가 보안 검증"
    echo ""
    echo "  [핵심 시연 시나리오 — payment saga]"
    echo "  scenario/04_payment_saga/01_payment_failure → 결제 수단 오류 보상"
    echo "  scenario/04_payment_saga/02_stock_failure   → 재고 차감 실패 결제 취소"
    echo "  scenario/04_payment_saga/03_hold_expire     → 구매 hold 만료"
    echo "  scenario/04_payment_saga/04_idempotency     → PG 오류 결제 멱등성"
    echo ""
    echo "  [시연 시나리오 그룹]"
    echo "  scenario                        → 핵심 시연 시나리오 전체"
    echo "  scenario/01_drop_purchase       → 드롭 시나리오 전체"
    echo "  scenario/02_raffle_purchase     → 래플 시나리오 전체"
    echo "  scenario/03_coupon_concurrency  → 쿠폰 동시 발급 시나리오 전체"
    echo "  scenario/04_payment_saga        → 결제 실패 시나리오 전체"
    echo "  scenario/05_admin               → 관리자 운영 시나리오 전체"
    echo "  scenario/06_auth_errors         → 권한 오류 시나리오 전체"
    echo ""
    echo "  [시연 시나리오 개별]"
    echo "  scenario/01_drop_purchase/01_normal                 → 드롭 정상 구매 해피패쓰"
    echo "  scenario/02_raffle_purchase/01_entry_with_coupon    → 응모 (쿠폰 적용 + 선결제)"
    echo "  scenario/02_raffle_purchase/02_draw_and_notify      → 추첨 → 당첨 알림 / 낙첨 자동 환불 + 알림"
    echo "  scenario/03_coupon_concurrency/01_concurrent_issue  → 동시 발급 → 수량만 성공"
    echo "  scenario/03_coupon_concurrency/02_duplicate_issue   → 중복 발급 차단"
    echo "  scenario/04_payment_saga/01_payment_failure         → 결제 수단 오류 → 재고/쿠폰 롤백 + 실패 알림"
    echo "  scenario/04_payment_saga/02_stock_failure           → 재고 차감 실패 → 결제 승인 취소 보상"
    echo "  scenario/04_payment_saga/03_hold_expire             → 구매 hold 만료 → 선점 자동 해제"
    echo "  scenario/05_admin/01_drop_modify                    → 오픈 전 드롭 수정/삭제"
    echo "  scenario/05_admin/02_raffle_modify                  → 오픈 전 래플 수정/삭제"
    echo "  scenario/05_admin/03_raffle_status                  → 래플 상태 강제 변경 (SCHEDULED→OPEN→CLOSED)"
    echo "  scenario/05_admin/04_raffle_draw                    → 응모자 목록 조회 + 수동 추첨"
    echo "  scenario/06_auth_errors/00_signup_happy             → 회원가입 해피패쓰"
    echo "  scenario/06_auth_errors/01_unauthenticated          → 비로그인 접근 차단"
    echo "  scenario/06_auth_errors/02_unauthorized             → 권한 오류"
    echo "  scenario/06_auth_errors/03_token_refresh            → 토큰 갱신"
    exit 1
  fi
fi

JAVA_HOME=$JAVA_HOME \
GATEWAY_SECRET=$GATEWAY_SECRET \
ADMIN_SECRET=$ADMIN_SECRET \
PAYMENT_SERVICE_URL=${PAYMENT_SERVICE_URL:-http://localhost:8085} \
KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092} \
./gradlew :e2e:test -PrunE2E \
  $([ -n "$KARATE_OPTS" ] && echo "-Dkarate.options=$KARATE_OPTS")
