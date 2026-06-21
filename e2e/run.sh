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
#   saga                → saga/ 폴더 전체 (서비스 연계 시나리오)
#
#   [개별 시나리오]
#   user/signup         → 회원가입 (정상 201 / 중복 409 / 어드민 201)
#   user/login          → 로그인 (정상 200 + 토큰 발급 / 잘못된 비밀번호 401)
#   user/profile        → 프로필 조회 (정상 200 / 토큰 없음 401)
#   user/token_refresh  → 토큰 갱신 (정상 200 / 잘못된 토큰 401)
#   user/profile_update → 프로필 수정 (정상 200 / 탈퇴 200 / 미인증 401)
#   user/address        → 주소 CRUD + 기본 주소 설정
#   user/security       → 인증·인가·경로 보안 검증
#
# ----------------------------------------------------------------
# 예시
# ----------------------------------------------------------------
#
#   bash e2e/run.sh                   # 모든 시나리오 실행
#   bash e2e/run.sh user              # user 서비스 시나리오 전체
#   bash e2e/run.sh user/signup         # 회원가입 시나리오만
#   bash e2e/run.sh user/login          # 로그인 시나리오만
#   bash e2e/run.sh user/profile        # 프로필 조회 시나리오만
#   bash e2e/run.sh user/token_refresh  # 토큰 갱신 시나리오만
#   bash e2e/run.sh user/profile_update # 프로필 수정 시나리오만
#   bash e2e/run.sh user/address        # 주소 관리 시나리오만
#   bash e2e/run.sh user/security       # 보안 시나리오만
#
# ================================================================
# 사전 조건: 전체 인프라가 기동된 상태여야 한다
# ================================================================
#
#   docker compose up -d
#   docker compose -f docker-compose.services.yml up -d

GATEWAY_SECRET=local-secret
ADMIN_SECRET=local-admin-secret

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
VALID_TARGETS="user saga user/signup user/login user/profile user/token_refresh user/profile_update user/address user/security"
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
    echo "  saga              → saga/ 폴더 전체"
    echo ""
    echo "  [개별 시나리오]"
    echo "  user/signup         → 회원가입"
    echo "  user/login          → 로그인"
    echo "  user/profile        → 프로필 조회"
    echo "  user/token_refresh  → 토큰 갱신"
    echo "  user/profile_update → 프로필 수정 및 탈퇴"
    echo "  user/address        → 주소 CRUD + 기본 주소 설정"
    echo "  user/security       → 인증·인가·경로 보안 검증"
    exit 1
  fi
fi

GATEWAY_SECRET=$GATEWAY_SECRET \
ADMIN_SECRET=$ADMIN_SECRET \
./gradlew :e2e:test -PrunE2E \
  $([ -n "$KARATE_OPTS" ] && echo "-Dkarate.options=$KARATE_OPTS")
