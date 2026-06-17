---
name: frontend-feature-build
description: 확정된 계약을 기준으로 React+TypeScript+shadcn/ui 화면 슬라이스(로그인/탐색기/업로드/공유/버전/휴지통/알림)를 일관된 절차로 구현할 때 사용한다. 계약에 없는 응답 필드를 가정해 화면을 만드는 데는 쓰지 않는다.
---

# Frontend Feature Build

## Overview

이 Skill은 프런트엔드 엔지니어가 한 화면 슬라이스를 **API 클라이언트→React Query 훅→컴포넌트→라우트** 순서로 일관되게 만들도록 돕는다. 응답 타입은 항상 `artifacts/01-architecture/api-contract.md`와 일치시키고, 다른 응답이 필요해 보이면 가정하지 않고 백엔드/설계자에 질문한다.

## Workflow

1. 대상 화면이 사용하는 엔드포인트·이벤트를 계약에서 읽고 TypeScript 타입을 정의한다.
2. **API 클라이언트**: 계약 타입에 맞는 fetch 래퍼(인증 헤더, 토큰 갱신 인터셉터, 에러 매핑).
3. **React Query 훅**: 조회 useQuery / 변경 useMutation + 캐시 무효화.
4. **컴포넌트**: shadcn/ui로 화면 구성. 업로드는 진행률·재시도, 목록은 빠른 렌더(체감 500ms).
5. **라우트**: React Router 경로와 인증 가드.
6. **실시간**: 해당하면 STOMP 구독으로 알림 수신 → 캐시 무효화(1초 이내 반영, 다중 디바이스 동기화).
7. 타입체크·빌드 실행 후 `artifacts/03-frontend/{feature}.md`에 기록, qa-reviewer에 완료 메시지.

## 산출물 형식 (`artifacts/03-frontend/{feature}.md`)

```md
## 화면: {auth|explorer|upload|share|versions|trash|notifications}
## 사용 엔드포인트/이벤트
-
## 계약 대조
- [x] 응답 타입 일치 (계약에 없는 필드 미사용)
- [x] 인증/토큰 갱신 처리
- [x] 실시간 구독·캐시 무효화 (해당 시)
## 빌드·타입체크
- 명령:
- 결과:
## 확인 필요
-
```

## 품질 기준

- 모든 API 호출 타입이 계약과 일치하고 계약에 없는 필드를 만들지 않는다.
- 토큰 만료 시 자동 갱신 또는 재로그인 흐름이 있다.
- 업로드 진행률·실패 재시도가 동작한다.
- 실시간 이벤트가 화면에 1초 이내 반영된다.
- 모바일 폭에서 레이아웃이 깨지지 않는다.

## 예외와 금지 행동

- 계약에 없는 응답을 가정해 화면을 만들지 않는다.
- `backend/`·인프라 설정을 수정하지 않는다.
- 백엔드 미완성 구간은 계약 기준 mock으로 진행하되 mock 사용 사실을 산출물에 표시한다.
