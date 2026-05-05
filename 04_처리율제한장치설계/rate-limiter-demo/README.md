# 토큰 버킷 데모

**위치:** `04_처리율제한장치설계/token-bucket-demo/`
**스택:** Spring Boot 3.4.5 / Java 17 / Thymeleaf / Vanilla JS / SSE

## 동작과 구현

`TokenBucket`은 스레드 안전한(원자적 연산 기반) 토큰 버킷이다. 스케줄러(리필러)가 주기적으로 토큰을 채우고, API 호출 시 서블릿 필터가 토큰을 소비한다. 소비·거부·리필이 일어날 때마다 SSE 이벤트로 모든 브라우저에 푸시되어 게이지·로그가 실시간으로 갱신된다.

```
[버튼] → fetch /api/ping → Filter → RateLimiter → TokenBucket
                                              ↘ Publisher → SSE → 모든 브라우저
[10초마다] → Scheduler → RateLimiter.refill → TokenBucket
                                           ↘ Publisher → SSE → 모든 브라우저
```

## 동작 파라미터

- **Capacity:** 5 토큰 (앱 시작 시 가득)
- **Refill:** 10초당 1토큰
- **거부:** HTTP 429 + `{"error":"Too Many Requests"}` + `X-Ratelimit-*` 3종 헤더

## 실행

```bash
cd 04_처리율제한장치설계/token-bucket-demo
./gradlew bootRun
# 브라우저: http://localhost:8080
```

## 의도적으로 뺀 것

분산 환경 / Redis / IP·사용자별 버킷 / 다른 알고리즘 비교 / Docker — 학습 목적이라 단일 글로벌 버킷 1개로 충분.
