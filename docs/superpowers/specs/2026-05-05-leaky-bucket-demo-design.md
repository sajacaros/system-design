# 누출 버킷 데모 설계 (Leaky Bucket Demo)

**위치 (예정):** `04_처리율제한장치설계/rate-limiter-demo/`
**스택:** Spring Boot 3.4.5 / Java 17 / Thymeleaf / Vanilla JS / SSE (기존 token-bucket-demo와 동일)
**작성일:** 2026-05-05

## 1. 목적

토큰 버킷 데모 옆에 누출 버킷 데모를 만들어, 두 알고리즘의 거동 차이를 한 화면에서 체감할 수 있게 한다. 핵심 학습 포인트:

- **토큰 버킷**: refill rate가 핵심. 버스트 허용. 응답이 즉시 sync(200/429).
- **누출 버킷**: queue size가 핵심. 처리율이 균일. 응답이 async(202 + reqId, 나중에 processed 이벤트).

## 2. 범위 결정

### 2.1 통합 방식: 탭

기존 `token-bucket-demo` 모듈을 `rate-limiter-demo`로 리네임하고, 한 앱 안에서 두 알고리즘이 **독립 상태로 동시에 동작**한다. 사용자는 탭으로 화면만 전환한다.

대안으로 별도 모듈(sibling demo)도 검토했으나, 한 화면에서 비교할 수 있다는 학습 효과가 더 컸다.

### 2.2 누출 버킷 응답 의미: 즉시 응답 (Enqueue-only)

`/api/leaky/ping` 호출 시 큐에 자리 있으면 `202 Accepted` + `reqId`를 즉시 반환. 실제 "처리"는 누수 스케줄러가 큐에서 꺼낼 때 SSE `processed` 이벤트로 알린다.

- 토큰 버킷의 sync 응답(200/429)과 대비되는 async 의미가 핵심.
- 클라이언트 관점에서는 Future 패턴 비유 — `reqId`가 Future 핸들, `processed` SSE가 완료 콜백.
- 서버 내부에는 `CompletableFuture`를 두지 **않는다**. 큐 + SSE만으로 데모가 충분하고, 단순함을 깨지 않기 위함.

대안으로 블록형(자기 차례에 누수될 때까지 응답 보류)도 검토했으나, 빠르게 클릭하면 응답이 다 멈춘 것처럼 보여 데모로는 답답하다고 판단.

### 2.3 파라미터

| 알고리즘 | capacity | rate |
|---|---|---|
| Token Bucket | 5 (기존 유지) | 1 token / 10s |
| Leaky Bucket | 15 | 1 leak / 3s |

큐 크기를 토큰 버킷의 3배로 키운 것은 누출 버킷의 정체성("받아주는 양은 큐 크기만큼, 처리율은 균일")을 시각화하기 위함이다. 누수 속도(3s)는 큐 드레인 애니메이션이 살아나도록 데모 친화적으로 설정. 동일 throughput 비교는 의도적으로 포기 — README에서 명시.

## 3. 아키텍처

### 3.1 모듈 / 패키지 레이아웃

```
04_처리율제한장치설계/rate-limiter-demo/   ← token-bucket-demo에서 디렉토리 리네임
└── src/main/java/kr/study/
    ├── tokenbucket/                    ← 기존 코드 유지, config만 분리
    │   ├── bucket/TokenBucket.java
    │   ├── config/TokenBucketConfig.java        ← 신규 (기존 RateLimiterConfig에서 분리)
    │   ├── ratelimit/RateLimiter.java, RateLimitFilter.java
    │   └── stream/BucketEventPublisher.java, BucketStreamController.java
    ├── leakybucket/                    ← 신규
    │   ├── bucket/LeakyBucket.java
    │   ├── config/LeakyBucketConfig.java
    │   ├── ratelimit/LeakyRateLimiter.java, LeakyRateLimitFilter.java
    │   └── stream/LeakyEventPublisher.java, LeakyStreamController.java
    └── controller/
        ├── HomeController.java          ← 두 알고리즘 파라미터 모두 모델 주입
        └── PingController.java          ← /api/token/ping, /api/leaky/ping 분기
```

기존 `kr.study.tokenbucket.config.RateLimiterConfig`는 삭제 (역할이 `TokenBucketConfig`로 이동).

### 3.2 엔드포인트

| 변경 전 | 변경 후 | 비고 |
|---|---|---|
| `/api/ping` | `/api/token/ping` | 리네임 |
| `/api/bucket/stream` | `/api/token/stream` | 리네임 |
| (없음) | `/api/leaky/ping` | 신규 |
| (없음) | `/api/leaky/stream` | 신규 |
| `/` | `/` | 탭 UI로 확장 |

UI도 함께 변경되므로 호환성 부담은 없다.

## 4. 핵심 컴포넌트

### 4.1 `LeakyBucket` (스레드 안전 큐)

- 내부 자료구조: `LinkedBlockingDeque<String>` (reqId 보관)
- 메소드:
  - `tryEnqueue(String reqId) → boolean`: `size() < capacity`면 enqueue + true, 아니면 false
  - `pollOne() → Optional<String>`: head 꺼냄
  - `size() → int`, `capacity() → int`

### 4.2 `LeakyRateLimiter` (`RateLimiter`와 대칭)

- `tryEnqueue() → Outcome` — `ACCEPTED(reqId)` 또는 `REJECTED`
  - reqId = `UUID.randomUUID().toString().substring(0, 8)`
  - 결과에 따라 `LeakyEventPublisher`에 `accepted` / `rejected` 이벤트 발행
- `leakOne()`: 큐 폴링 → `processed` 이벤트 발행

### 4.3 `LeakyRateLimitFilter` (서블릿 필터)

- `/api/leaky/ping` 인터셉트
- ACCEPTED → `202` + 헤더 `X-RateLimit-RequestId`, body `{"reqId":"abc12345"}`
- REJECTED → `429` + `{"error":"queue full"}`

### 4.4 Leaker (스케줄러)

`LeakyBucketConfig`에서 `ScheduledExecutorService`로 3초 주기 `LeakyRateLimiter.leakOne()` 호출. 토큰 버킷 리필러와 별도 풀(이름: `leaky-bucket-leaker`).

### 4.5 `LeakyEventPublisher` / `LeakyStreamController`

- 토큰 버킷의 `BucketEventPublisher` / `BucketStreamController`와 동일 패턴
- 이벤트: `init`, `accepted`, `rejected`, `processed`
- 페이로드: `{ size: int, capacity: int, reqId?: string }`

## 5. 데이터 흐름

### 토큰 버킷 (기존, 엔드포인트만 리네임)
```
[Click → /api/token/ping]
  → RateLimitFilter → RateLimiter.tryConsume()
    → 200 즉시 (or 429)
    → SSE consume / rejected 이벤트
[10s마다 Refiller → SSE refill]
```

### 누출 버킷 (신규)
```
[Click → /api/leaky/ping]
  → LeakyRateLimitFilter → LeakyRateLimiter.tryEnqueue()
    ✓ 202 + reqId 즉시 반환
      → SSE "accepted" {reqId, size, capacity}
    ✗ 429 반환
      → SSE "rejected" {size, capacity}

[3s마다 Leaker]
  → LeakyRateLimiter.leakOne()
    → SSE "processed" {reqId, size, capacity}
```

## 6. UI / 프론트엔드

### 6.1 페이지 구조 (`index.html` 확장)

```
Rate Limiter Demo  [● connected]
┌─[ Token Bucket ]─[ Leaky Bucket ]─┐
│   (활성 탭의 패널만 표시)         │
└────────────────────────────────────┘
```

- 탭 두 개, 기본 활성 = Token Bucket
- 탭 전환은 CSS class 토글
- **두 SSE 스트림은 진입 시점부터 둘 다 구독** — 백그라운드에서 상태 갱신, 탭 전환해도 이전 탭 상태 유지
- `connected` 상태는 두 스트림 모두 OK일 때만

### 6.2 Token Bucket 패널 (기존 유지)
- 헤더: `capacity = 5 · refill: 1 token / 10s`
- 가로 점 5개 게이지
- "Call /api/token/ping" 버튼
- 로그

### 6.3 Leaky Bucket 패널 (신규)

**상단 메타**: `capacity = 15 · leak rate: 1 req / 3s`

**중앙 워터탱크 (세로):**
- 15칸 세로 스택, 아래에서 위로 차오름
- 빈 칸: light gray / 채워진 칸: 파랑
- enqueue: bottom-most empty 칸이 파랑으로 (transition 0.2s)
- leak: 가장 아래 채워진 칸이 비고 위 칸들이 한 칸씩 내려감 (CSS transform translateY)
- `size === capacity`일 때 최상위 1-2칸이 `@keyframes pulse-red` 깜빡

**하단 누수구**: 작은 노즐 + `processed` 이벤트 시 `💧` 텍스트 0.5s 페이드인-아웃

**버튼**: "Call /api/leaky/ping"

**로그** (요청 수명 2단계가 핵심):
```
[14:23:15.402] 202 Accepted   reqId=abc12345  size=3/15
[14:23:18.103] ✓ Processed    reqId=abc12345  size=2/15
[14:23:21.488] 429 Rejected                   size=15/15  queue full
```

- 같은 `reqId`의 두 row → 호버 시 둘 다 highlight
- accepted / processed / rejected 색 구분 (파랑 / 초록 / 빨강)

### 6.4 로그 / SSE 책임 분담 (단일 SSE 파이프라인)

데모용이라 다중 사용자 호출을 가정하지 않는다. **로그·게이지 갱신을 모두 SSE 이벤트 핸들러에서 처리**해 두 패널이 동일한 패턴을 갖는다. fetch 응답 핸들러는 네트워크 오류만 처리하고 화면에 직접 영향을 주지 않는다.

**Token Bucket 패널** (기존 fetch-기반 로그를 SSE로 이전 — refill 이벤트도 자동 로깅됨)

| SSE 이벤트 | 로그 | 게이지 |
|---|---|---|
| `init` | (없음) | 초기 토큰 수 렌더 |
| `consume` | `200 OK tokens=N` | 갱신 |
| `refill` | `↻ Refill tokens=N` | 갱신 |
| `rejected` | `429 Rejected tokens=0` | 빨강 깜빡 |

**Leaky Bucket 패널**

| SSE 이벤트 | 로그 | 게이지 |
|---|---|---|
| `init` | (없음) | 초기 큐 사이즈 렌더 |
| `accepted` | `202 Accepted reqId=... size=N/15` | +1칸 |
| `processed` | `✓ Processed reqId=... size=N/15` | -1칸, 💧 애니메이션 |
| `rejected` | `429 Rejected size=15/15 queue full` | 가득 찬 표시 깜빡 |

이 결정에 따라 응답 헤더에 size를 별도로 내릴 필요는 없다 (SSE 페이로드의 `size`로 충분). 기존 token-bucket UI도 SSE 단일 파이프라인으로 살짝 수정된다.

탭마다 별개 EventSource: `/api/token/stream`, `/api/leaky/stream`.

## 7. 테스트

`LeakyBucketTest` 한 클래스 (기존 `TokenBucketTest`와 동일 스타일):

- `capacity_만큼_enqueue_성공_후_다음은_실패한다`
- `enqueue_후_poll하면_FIFO_순서로_나온다`
- `비어있는_큐_poll은_빈값을_반환한다`
- `가득_찬_상태에서_poll_후_다시_enqueue_가능하다`

기존 `TokenBucketTest`는 패키지 이동이 없으므로 import 변경 불필요. `RateLimiterConfig` 분리에 따른 빈 등록 테스트는 별도로 작성하지 않음 (Spring Boot 통합 테스트 범위 밖).

## 8. 의도적으로 뺀 것

- 분산 환경 / Redis / IP·사용자별 큐 / Docker — 학습 목적이라 단일 글로벌 큐로 충분
- 시간축 차트(초당 입력 vs 처리) — 게이지 + 로그로 충분히 전달, 차트는 욕심
- 누수 속도 슬라이더 — 학습 데모로는 단순함이 우선
- `CompletableFuture` 기반 서버 내부 비동기 — 큐 + SSE로 충분, future 도입은 단순함을 깸
- 두 알고리즘 동시 비교용 차트 페이지 — 탭으로 충분

## 9. 마이그레이션 / 영향

- `04_처리율제한장치설계/token-bucket-demo/` 디렉토리가 `rate-limiter-demo/`로 리네임 → 기존 README 링크 수정 필요
- `/api/ping`, `/api/bucket/stream` 엔드포인트가 `/api/token/*`로 리네임 → 외부 호출자 없으므로 안전
- `RateLimiterConfig.java` 삭제, `TokenBucketConfig.java`로 이동
