# 처리율 제한 데모 (Rate Limiter Demo)

**위치:** `04_처리율제한장치설계/rate-limiter-demo/`
**스택:** Spring Boot 3.4.5 / Java 17 / Thymeleaf / Vanilla JS / SSE

토큰 버킷과 누출 버킷 두 알고리즘을 한 앱에서 탭으로 비교한다.

## 알고리즘 비교

| | Token Bucket | Leaky Bucket |
|---|---|---|
| capacity | 5 | 15 |
| rate | refill: 1 token / 10s | leak: 1 req / 3s |
| 응답 | sync — `200 OK` / `429` | async — `202 Accepted` + `reqId`, 누수 시 SSE `processed` |
| 버스트 | ✅ capacity까지 즉시 | ❌ 큐가 받기는 하나 처리율은 균일 |
| 핵심 | refill rate가 처리량을 결정 | 큐 크기 = 버퍼, 처리율 = 일정 |

데모 편의상 두 알고리즘 throughput은 의도적으로 다르게 설정 (누출 버킷의 큐 드레인 애니메이션을 위해 1/3s).

## 동작 흐름

```
[Token]   클릭 → /api/token/ping → RateLimitFilter → TokenBucket
                                              ↘ Publisher → SSE → 모든 브라우저
          [10s마다] Scheduler → refill ↘
          
[Leaky]   클릭 → /api/leaky/ping → LeakyRateLimitFilter → LeakyBucket
                                              ↘ Publisher → SSE (accepted/rejected)
          [3s마다] Leaker → pollOne ↘ Publisher → SSE (processed, 💧)
```

누출 버킷의 응답은 Future 패턴 비유 — `reqId`가 핸들, SSE `processed`가 완료 콜백.

## 실행

```bash
cd 04_처리율제한장치설계/rate-limiter-demo
./gradlew bootRun
# 브라우저: http://localhost:8080
```

## 의도적으로 뺀 것

분산 환경 / Redis / IP·사용자별 버킷 / Docker / 시간축 차트 / 누수 속도 슬라이더 — 학습 목적이라 단일 글로벌 인스턴스로 충분.
