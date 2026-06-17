## 모듈: auth

## 구현 범위
- 엔드포인트: `POST /api/auth/signup`(201 {id,email,nickname}), `POST /api/auth/login`(200 {accessToken,refreshToken,user}), `POST /api/auth/refresh`(200 {accessToken,refreshToken}, 회전), `POST /api/auth/logout`(204, 인증 필요).
- 엔티티/테이블: `users`(BCrypt 해시), `refresh_token`(token_hash=SHA-256, expires_at, revoked).
- JWT: access 30분/refresh 14일(환경변수). Refresh는 서명 JWT(jti 포함) + 서버에 해시 저장 → 회전 시 기존 revoke, 신규 발급. logout 시 해당 refresh revoked=true.

## 계약 대조
- [x] 요청/응답 필드 일치
- [x] 401 BAD_CREDENTIALS / 409 EMAIL_TAKEN / 401 INVALID_REFRESH(만료·회수·위조)
- [x] 토큰 회전(기존 무효화) 필수 동작

## 빌드·테스트
- 명령: `./gradlew test --tests '*AuthFlowTest'`
- 결과: 통과 2 / 실패 0 (signup·중복409·login·refresh회전·구토큰401·logout후401, BAD_CREDENTIALS).

## 확인 필요
- 없음.
