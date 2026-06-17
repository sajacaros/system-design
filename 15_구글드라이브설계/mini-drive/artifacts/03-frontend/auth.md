## 화면: auth
회원가입/로그인/로그아웃, 토큰 저장·자동갱신.

## 사용 엔드포인트/이벤트
- POST /api/auth/signup → { id, email, nickname }
- POST /api/auth/login → { accessToken, refreshToken, user }
- POST /api/auth/refresh → { accessToken, refreshToken } (401 인터셉터에서 자동 호출)
- POST /api/auth/logout (204)

## 계약 대조
- [x] 응답 타입 일치 (계약에 없는 필드 미사용)
- [x] 인증/토큰 갱신 처리 — `src/api/client.ts`의 401 인터셉터가 단일 refresh로 재시도, 실패 시 토큰 폐기 후 /login 가드.
- [x] refresh 회전: 응답의 신규 accessToken+refreshToken을 모두 저장(`tokenStore.setTokens`).
- [ ] 실시간 구독·캐시 무효화 (해당 없음)

## 빌드·타입체크
- 명령: `pnpm install` → `pnpm build` (tsc --noEmit && vite build)
- 결과: 통과 (tsc 0 errors, vite built)

## 확인 필요
- 로그인 응답의 user 객체는 { id, email, nickname }로 저장. 계약 일치.
- 비밀번호 정책(길이/복잡도)은 계약에 명시 없음 — 클라이언트는 required만 검증, 서버 VALIDATION 코드를 그대로 표시.
