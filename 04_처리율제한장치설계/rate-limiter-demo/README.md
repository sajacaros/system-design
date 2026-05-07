# 처리율 제한 데모 (Rate Limiter Demo)

**위치:** `04_처리율제한장치설계/rate-limiter-demo/`
**스택:** Spring Boot 3.4.5 / Java 17 / Thymeleaf / Vanilla JS / SSE

토큰 버킷, 누출 버킷, 고정 윈도 카운터, 이동 윈도 로그 네 알고리즘을 한 앱에서 탭으로 비교한다.

## 알고리즘 비교

| | Token Bucket | Leaky Bucket | Fixed Window | Sliding Window Log |
|---|---|---|---|---|
| 한도 | capacity 5 | capacity 5 | 5 req / 30s | 5 req / 8s |
| rate | refill: 1 token / 10s | leak: 1 req / 3s | 윈도우 단위 reset | 슬라이딩 윈도(타임스탬프 만료) |
| 응답 | sync — `200 OK` / `429` | async — `202 Accepted` + `reqId`, 누수 시 SSE `processed` | sync — `200 OK` / `429` | sync — `200 OK` / `429` |
| 버스트 | ✅ capacity까지 즉시 | ❌ 큐가 받지만 처리율은 균일 | ⚠️ 윈도우 경계 부근에서 한도의 2배까지 통과 가능 | ❌ 어떤 순간에도 한도 초과 X |
| 핵심 | refill rate가 처리량을 결정 | 큐 크기 = 버퍼, 처리율 = 일정 | 단순·메모리 효율, 단점은 경계면 burst | 정확하지만 거부 요청도 보관해야 해서 메모리 비용 |

데모 편의상 네 알고리즘의 시간 단위는 의도적으로 다르게 설정 (누출 버킷은 큐 드레인 애니메이션 위해 1/3s, Fixed Window는 경계면 burst를 직접 노릴 시간이 있도록 30s 윈도우, Sliding Log는 윈도 8s + 컬럼 한 칸이 2s 범위라 점프가 잦지 않으면서도 흐름이 잘 보이게 함).

## 동작 흐름

```
[Token]   클릭 → /api/token/ping → RateLimitFilter → TokenBucket
                                              ↘ Publisher → SSE → 모든 브라우저
          [10s마다] Scheduler → refill ↘

[Leaky]   클릭 → /api/leaky/ping → LeakyRateLimitFilter → LeakyBucket
                                              ↘ Publisher → SSE (accepted/rejected)
          [3s마다] Leaker → pollOne ↘ Publisher → SSE (processed, 💧)

[Fixed]   클릭 → /api/fixed/ping → FixedWindowRateLimitFilter → FixedWindowCounter
                                              ↘ Publisher → SSE (window_open / accepted / rejected)
          [1s마다] Tick → 윈도우 만료 시 reset + window_open 발행

[Sliding] 클릭 → /api/sliding-log/ping → SlidingWindowLogRateLimitFilter → SlidingWindowLog
                                              ↘ Publisher → SSE (init / accepted / rejected, entries 스냅샷 포함)
          매 요청마다 만료 타임스탬프 제거 + 새 ts 추가 + size 체크
```

누출 버킷의 응답은 Future 패턴 비유 — `reqId`가 핸들, SSE `processed`가 완료 콜백.
Fixed Window UI는 윈도우 단위로 가로 슬롯을 쌓으며, 최신 윈도우가 왼쪽에 위치한다 (최대 10개). 윈도우 경계에서 빠르게 클릭하면 한 윈도우의 마지막 + 다음 윈도우의 처음에 각각 5개씩 통과해 짧은 시간 내 10개가 처리되는 fixed window의 단점이 그대로 보인다.

Sliding Log UI는 좌측 끝이 "지금"이고 시간이 흐르면 모든 점이 우측으로 흐른다. 윈도 박스(좌측 10초 영역) 안에 있는 entry만 한도 계산에 들어가고, 같은 초의 요청은 한 컬럼에 누적된다 — 통과한 ●는 위쪽 5칸 슬롯에 위→아래로, 거부된 요청은 컬럼 바닥에 `✕×N` 카운트로 표시된다. 한 번 한도가 초과되면 거부된 타임스탬프도 윈도가 흘러가기 전까지 한도를 차지해 추가 거부를 유발하는 모습이 그대로 드러난다 (책에서 말하는 메모리 단점).

## 실행

```bash
cd 04_처리율제한장치설계/rate-limiter-demo
./gradlew bootRun
# 브라우저: http://localhost:8080
```

## 의도적으로 뺀 것

분산 환경 / Redis / IP·사용자별 버킷 / Docker / 시간축 차트 / 누수 속도 슬라이더 — 학습 목적이라 단일 글로벌 인스턴스로 충분.
