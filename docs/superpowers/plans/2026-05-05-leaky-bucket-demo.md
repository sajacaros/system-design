# Leaky Bucket Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 `token-bucket-demo` 모듈을 `rate-limiter-demo`로 확장하고, 누출 버킷(Leaky Bucket) 알고리즘을 추가해 한 앱에서 두 알고리즘을 탭 UI로 비교한다.

**Architecture:** 토큰 버킷과 대칭되는 패키지 구조(`kr.study.leakybucket.*`)로 누출 버킷 컴포넌트를 신규 작성. 누출 버킷 응답은 즉시 `202 Accepted` + `reqId`로 끊고, 누수 스케줄러가 큐에서 꺼낼 때 SSE `processed` 이벤트로 처리 완료를 알린다. 두 패널 모두 로그·게이지를 SSE 단일 파이프라인에서 갱신한다.

**Tech Stack:** Spring Boot 3.4.5 / Java 17 / Thymeleaf / Vanilla JS / SSE / Gradle / JUnit 5 + AssertJ (기존과 동일)

**Spec Reference:** `docs/superpowers/specs/2026-05-05-leaky-bucket-demo-design.md`

---

## File Structure

### 모듈 디렉토리 (rename)
- `04_처리율제한장치설계/token-bucket-demo/` → `04_처리율제한장치설계/rate-limiter-demo/`

### 기존 파일 수정
| 파일 | 변경 |
|---|---|
| `settings.gradle` | rootProject.name `'token-bucket-demo'` → `'rate-limiter-demo'` |
| `src/main/java/kr/study/tokenbucket/config/RateLimiterConfig.java` | 삭제 |
| `src/main/java/kr/study/tokenbucket/controller/PingController.java` | `/api/ping` → `/api/token/ping` |
| `src/main/java/kr/study/tokenbucket/stream/BucketStreamController.java` | `/api/bucket/stream` → `/api/token/stream` |
| `src/main/java/kr/study/tokenbucket/controller/HomeController.java` | leaky 파라미터 모델 주입 추가 |
| `src/main/resources/templates/index.html` | 탭 UI + leaky 패널 + JS SSE 일원화 |
| `README.md` (모듈 루트) | 누출 버킷 추가 설명 |

### 신규 파일 (8개)
```
src/main/java/kr/study/
├── tokenbucket/config/TokenBucketConfig.java         (RateLimiterConfig 분할)
└── leakybucket/
    ├── bucket/LeakyBucket.java
    ├── ratelimit/Outcome.java
    ├── ratelimit/LeakyRateLimiter.java
    ├── ratelimit/LeakyRateLimitFilter.java
    ├── stream/LeakyEventPublisher.java
    ├── stream/LeakyStreamController.java
    └── config/LeakyBucketConfig.java

src/test/java/kr/study/leakybucket/bucket/LeakyBucketTest.java
```

---

## Task 1: 모듈 디렉토리 리네임

**Files:**
- Rename: `04_처리율제한장치설계/token-bucket-demo/` → `04_처리율제한장치설계/rate-limiter-demo/`
- Modify: `04_처리율제한장치설계/rate-limiter-demo/settings.gradle`

- [ ] **Step 1: 디렉토리 git mv**

```bash
cd /home/sajacaros/workspace/system-design
git mv 04_처리율제한장치설계/token-bucket-demo 04_처리율제한장치설계/rate-limiter-demo
git status
```

Expected: 디렉토리 내 모든 파일이 renamed로 표시됨.

- [ ] **Step 2: settings.gradle 프로젝트명 변경**

`04_처리율제한장치설계/rate-limiter-demo/settings.gradle`을 다음과 같이 수정:

```groovy
rootProject.name = 'rate-limiter-demo'
```

- [ ] **Step 3: gradle 빌드 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew build
```

Expected: BUILD SUCCESSFUL (기존 `TokenBucketTest` 통과)

- [ ] **Step 4: 커밋**

```bash
cd /home/sajacaros/workspace/system-design
git add -A
git commit -m ":truck: token-bucket-demo → rate-limiter-demo 모듈 리네임

누출 버킷 알고리즘 추가에 앞서 모듈 이름을 일반화."
```

---

## Task 2: RateLimiterConfig를 TokenBucketConfig로 분할

**Files:**
- Delete: `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/config/RateLimiterConfig.java`
- Create: `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/config/TokenBucketConfig.java`

- [ ] **Step 1: 새 TokenBucketConfig 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/config/TokenBucketConfig.java`:

```java
package kr.study.tokenbucket.config;

import kr.study.tokenbucket.bucket.TokenBucket;
import kr.study.tokenbucket.ratelimit.RateLimitFilter;
import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class TokenBucketConfig {

    public static final int CAPACITY = 5;
    public static final int REFILL_INTERVAL_SECONDS = 10;

    @Bean
    public TokenBucket tokenBucket() {
        return new TokenBucket(CAPACITY, CAPACITY);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter) {
        FilterRegistrationBean<RateLimitFilter> reg =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter));
        reg.addUrlPatterns("/api/token/ping");
        reg.setName("rateLimitFilter");
        return reg;
    }

    @Bean(name = "tokenBucketRefillScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService refillScheduler(RateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-bucket-refill");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                limiter::refill,
                REFILL_INTERVAL_SECONDS,
                REFILL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        return scheduler;
    }
}
```

차이점: 클래스명 `RateLimiterConfig` → `TokenBucketConfig`, 빈 이름 `tokenBucketRefillScheduler`로 명시(다음 태스크에서 leaker 스케줄러와 충돌 회피), `addUrlPatterns("/api/token/ping")` (다음 태스크에서 컨트롤러도 같이 변경).

- [ ] **Step 2: 기존 RateLimiterConfig 삭제**

```bash
rm /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/config/RateLimiterConfig.java
```

- [ ] **Step 3: 컴파일 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 기존 테스트 통과 확인**

```bash
./gradlew test
```

Expected: `TokenBucketTest` 모두 통과.

- [ ] **Step 5: 커밋**

```bash
cd /home/sajacaros/workspace/system-design
git add -A
git commit -m ":recycle: RateLimiterConfig → TokenBucketConfig 분리

알고리즘별 패키지 안에 자기 config를 두는 대칭 구조로 정리.
누출 버킷 추가 준비."
```

---

## Task 3: 토큰 버킷 엔드포인트 리네임

**Files:**
- Modify: `src/main/java/kr/study/tokenbucket/controller/PingController.java`
- Modify: `src/main/java/kr/study/tokenbucket/stream/BucketStreamController.java`

(이전 Task에서 `TokenBucketConfig`의 URL 패턴은 이미 `/api/token/ping`으로 수정됨)

- [ ] **Step 1: PingController 엔드포인트 변경**

Modify `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/controller/PingController.java`, change `@GetMapping("/api/ping")` to `@GetMapping("/api/token/ping")`:

```java
package kr.study.tokenbucket.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/api/token/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        );
    }
}
```

- [ ] **Step 2: BucketStreamController 엔드포인트 변경**

Modify `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/stream/BucketStreamController.java`, change `/api/bucket/stream` to `/api/token/stream`:

```java
@GetMapping(path = "/api/token/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    SseEmitter emitter = publisher.subscribe();
    publisher.sendInitial(emitter, limiter.tokens(), limiter.capacity());
    return emitter;
}
```

- [ ] **Step 3: 빌드 / 기동 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew bootRun &
sleep 8
curl -i http://localhost:8080/api/token/ping
curl -N --max-time 1 http://localhost:8080/api/token/stream || true
kill %1
wait 2>/dev/null
```

Expected:
- `/api/token/ping` → 200 + `{"message":"pong",...}` + `X-Ratelimit-*` 헤더
- `/api/token/stream` → `text/event-stream` 응답, `event:init data:{"tokens":5,"capacity":5}` 라인

- [ ] **Step 4: 커밋**

```bash
git add -A
git commit -m ":truck: 토큰 버킷 엔드포인트 리네임 (/api/token/*)

누출 버킷(/api/leaky/*) 추가에 앞서 알고리즘별 네임스페이스로 분리."
```

---

## Task 4: LeakyBucket 자료구조 (TDD)

**Files:**
- Create: `src/test/java/kr/study/leakybucket/bucket/LeakyBucketTest.java`
- Create: `src/main/java/kr/study/leakybucket/bucket/LeakyBucket.java`

- [ ] **Step 1: 테스트 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/test/java/kr/study/leakybucket/bucket/LeakyBucketTest.java`:

```java
package kr.study.leakybucket.bucket;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketTest {

    @Test
    void capacity_만큼_enqueue_성공_후_다음은_실패한다() {
        LeakyBucket bucket = new LeakyBucket(3);

        assertThat(bucket.tryEnqueue("a")).isTrue();
        assertThat(bucket.tryEnqueue("b")).isTrue();
        assertThat(bucket.tryEnqueue("c")).isTrue();
        assertThat(bucket.tryEnqueue("d")).isFalse();
        assertThat(bucket.size()).isEqualTo(3);
    }

    @Test
    void enqueue_후_poll하면_FIFO_순서로_나온다() {
        LeakyBucket bucket = new LeakyBucket(3);
        bucket.tryEnqueue("a");
        bucket.tryEnqueue("b");
        bucket.tryEnqueue("c");

        assertThat(bucket.pollOne()).contains("a");
        assertThat(bucket.pollOne()).contains("b");
        assertThat(bucket.pollOne()).contains("c");
        assertThat(bucket.size()).isEqualTo(0);
    }

    @Test
    void 비어있는_큐_poll은_빈값을_반환한다() {
        LeakyBucket bucket = new LeakyBucket(3);

        Optional<String> result = bucket.pollOne();

        assertThat(result).isEmpty();
    }

    @Test
    void 가득_찬_상태에서_poll_후_다시_enqueue_가능하다() {
        LeakyBucket bucket = new LeakyBucket(2);
        bucket.tryEnqueue("a");
        bucket.tryEnqueue("b");
        assertThat(bucket.tryEnqueue("c")).isFalse();

        bucket.pollOne();

        assertThat(bucket.tryEnqueue("c")).isTrue();
        assertThat(bucket.size()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew test --tests LeakyBucketTest
```

Expected: 컴파일 에러 ("cannot find symbol class LeakyBucket")

- [ ] **Step 3: LeakyBucket 구현**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/bucket/LeakyBucket.java`:

```java
package kr.study.leakybucket.bucket;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;

public class LeakyBucket {

    private final int capacity;
    private final LinkedBlockingDeque<String> queue;

    public LeakyBucket(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.queue = new LinkedBlockingDeque<>(capacity);
    }

    public boolean tryEnqueue(String reqId) {
        return queue.offer(reqId);
    }

    public Optional<String> pollOne() {
        return Optional.ofNullable(queue.poll());
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return capacity;
    }
}
```

- [ ] **Step 4: 테스트 실행 → PASS 확인**

```bash
./gradlew test --tests LeakyBucketTest
```

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyBucket 자료구조 추가 (FIFO 큐 + capacity)"
```

---

## Task 5: Outcome 결과 타입

**Files:**
- Create: `src/main/java/kr/study/leakybucket/ratelimit/Outcome.java`

- [ ] **Step 1: Outcome 타입 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/ratelimit/Outcome.java`:

```java
package kr.study.leakybucket.ratelimit;

public sealed interface Outcome {

    record Accepted(String reqId) implements Outcome {}

    record Rejected() implements Outcome {}
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: Outcome sealed interface 추가 (Accepted/Rejected)"
```

---

## Task 6: LeakyEventPublisher (SSE 발행자)

**Files:**
- Create: `src/main/java/kr/study/leakybucket/stream/LeakyEventPublisher.java`

- [ ] **Step 1: LeakyEventPublisher 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/stream/LeakyEventPublisher.java`:

```java
package kr.study.leakybucket.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LeakyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LeakyEventPublisher.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("leaky emitter subscribed (total={})", emitters.size());
        return emitter;
    }

    public void sendInitial(SseEmitter emitter, int size, int capacity) {
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(payload(null, size, capacity)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("leaky sendInitial failed, emitter removed", e);
        }
    }

    public void publish(String event, String reqId, int size, int capacity) {
        String data = payload(reqId, size, capacity);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("leaky publish failed, emitter removed", e);
            }
        }
    }

    private String payload(String reqId, int size, int capacity) {
        StringBuilder sb = new StringBuilder("{\"size\":").append(size)
                .append(",\"capacity\":").append(capacity);
        if (reqId != null) {
            sb.append(",\"reqId\":\"").append(reqId).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
```

토큰 버킷의 `BucketEventPublisher`와 동일 패턴, 페이로드만 `size/capacity/reqId` 형태.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyEventPublisher 추가 (init/accepted/rejected/processed SSE)"
```

---

## Task 7: LeakyRateLimiter

**Files:**
- Create: `src/main/java/kr/study/leakybucket/ratelimit/LeakyRateLimiter.java`

- [ ] **Step 1: LeakyRateLimiter 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/ratelimit/LeakyRateLimiter.java`:

```java
package kr.study.leakybucket.ratelimit;

import kr.study.leakybucket.bucket.LeakyBucket;
import kr.study.leakybucket.stream.LeakyEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeakyRateLimiter {

    private final LeakyBucket bucket;
    private final LeakyEventPublisher events;

    public LeakyRateLimiter(LeakyBucket bucket, LeakyEventPublisher events) {
        this.bucket = bucket;
        this.events = events;
    }

    public Outcome tryEnqueue() {
        String reqId = UUID.randomUUID().toString().substring(0, 8);
        boolean ok = bucket.tryEnqueue(reqId);
        if (ok) {
            events.publish("accepted", reqId, bucket.size(), bucket.capacity());
            return new Outcome.Accepted(reqId);
        }
        events.publish("rejected", null, bucket.size(), bucket.capacity());
        return new Outcome.Rejected();
    }

    public void leakOne() {
        bucket.pollOne().ifPresent(reqId ->
                events.publish("processed", reqId, bucket.size(), bucket.capacity()));
    }

    public int size() {
        return bucket.size();
    }

    public int capacity() {
        return bucket.capacity();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

(주의: 현재 시점에는 `LeakyBucket` 빈이 등록되지 않아 ApplicationContext가 뜨지 않음. `LeakyBucketConfig`를 만드는 Task 10에서 해결됨. `compileJava`만 확인하고 다음으로 진행.)

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyRateLimiter 추가 (tryEnqueue/leakOne + reqId 발행)"
```

---

## Task 8: LeakyRateLimitFilter

**Files:**
- Create: `src/main/java/kr/study/leakybucket/ratelimit/LeakyRateLimitFilter.java`

- [ ] **Step 1: LeakyRateLimitFilter 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/ratelimit/LeakyRateLimitFilter.java`:

```java
package kr.study.leakybucket.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LeakyRateLimitFilter extends OncePerRequestFilter {

    private final LeakyRateLimiter limiter;

    public LeakyRateLimitFilter(LeakyRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Outcome outcome = limiter.tryEnqueue();
        response.setHeader("X-RateLimit-QueueCapacity", String.valueOf(limiter.capacity()));
        response.setContentType("application/json;charset=UTF-8");

        if (outcome instanceof Outcome.Accepted accepted) {
            response.setStatus(202);
            response.setHeader("X-RateLimit-RequestId", accepted.reqId());
            response.getWriter().write("{\"reqId\":\"" + accepted.reqId() + "\"}");
            return;
        }

        response.setStatus(429);
        response.getWriter().write("{\"error\":\"queue full\"}");
    }
}
```

`chain.doFilter()`를 호출하지 않음 — 필터가 직접 응답을 마무리. 따라서 `/api/leaky/ping` 컨트롤러는 따로 두지 않는다.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyRateLimitFilter 추가 (202/429 응답 직접 작성)"
```

---

## Task 9: LeakyStreamController

**Files:**
- Create: `src/main/java/kr/study/leakybucket/stream/LeakyStreamController.java`

- [ ] **Step 1: LeakyStreamController 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/stream/LeakyStreamController.java`:

```java
package kr.study.leakybucket.stream;

import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LeakyStreamController {

    private final LeakyEventPublisher publisher;
    private final LeakyRateLimiter limiter;

    public LeakyStreamController(LeakyEventPublisher publisher, LeakyRateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/leaky/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        publisher.sendInitial(emitter, limiter.size(), limiter.capacity());
        return emitter;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyStreamController 추가 (/api/leaky/stream)"
```

---

## Task 10: LeakyBucketConfig (와이어링)

**Files:**
- Create: `src/main/java/kr/study/leakybucket/config/LeakyBucketConfig.java`

- [ ] **Step 1: LeakyBucketConfig 작성**

Create `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/leakybucket/config/LeakyBucketConfig.java`:

```java
package kr.study.leakybucket.config;

import kr.study.leakybucket.bucket.LeakyBucket;
import kr.study.leakybucket.ratelimit.LeakyRateLimitFilter;
import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class LeakyBucketConfig {

    public static final int CAPACITY = 15;
    public static final int LEAK_INTERVAL_SECONDS = 3;

    @Bean
    public LeakyBucket leakyBucket() {
        return new LeakyBucket(CAPACITY);
    }

    @Bean
    public FilterRegistrationBean<LeakyRateLimitFilter> leakyRateLimitFilter(LeakyRateLimiter limiter) {
        FilterRegistrationBean<LeakyRateLimitFilter> reg =
                new FilterRegistrationBean<>(new LeakyRateLimitFilter(limiter));
        reg.addUrlPatterns("/api/leaky/ping");
        reg.setName("leakyRateLimitFilter");
        return reg;
    }

    @Bean(name = "leakyBucketLeakerScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService leakerScheduler(LeakyRateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "leaky-bucket-leaker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                limiter::leakOne,
                LEAK_INTERVAL_SECONDS,
                LEAK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        return scheduler;
    }
}
```

- [ ] **Step 2: 빌드 / 기동 확인**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew bootRun &
sleep 8
curl -i http://localhost:8080/api/leaky/ping
curl -i http://localhost:8080/api/leaky/ping
curl -i http://localhost:8080/api/leaky/ping
curl -N --max-time 1 http://localhost:8080/api/leaky/stream || true
kill %1
wait 2>/dev/null
```

Expected:
- `/api/leaky/ping` → `202 Accepted` + 헤더 `X-RateLimit-RequestId: <8자>`, `X-RateLimit-QueueCapacity: 15` + body `{"reqId":"..."}`
- `/api/leaky/stream` → `event:init data:{"size":N,"capacity":15}` 라인 (위 호출 누적분에 따라 N=2~3 가량)

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: LeakyBucketConfig 추가 (Filter·Leaker 와이어링)"
```

---

## Task 11: HomeController에 leaky 모델 주입

**Files:**
- Modify: `src/main/java/kr/study/tokenbucket/controller/HomeController.java`

- [ ] **Step 1: HomeController 업데이트**

Modify `04_처리율제한장치설계/rate-limiter-demo/src/main/java/kr/study/tokenbucket/controller/HomeController.java`:

```java
package kr.study.tokenbucket.controller;

import kr.study.leakybucket.config.LeakyBucketConfig;
import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import kr.study.tokenbucket.config.TokenBucketConfig;
import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RateLimiter tokenLimiter;
    private final LeakyRateLimiter leakyLimiter;

    public HomeController(RateLimiter tokenLimiter, LeakyRateLimiter leakyLimiter) {
        this.tokenLimiter = tokenLimiter;
        this.leakyLimiter = leakyLimiter;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tokenCapacity", tokenLimiter.capacity());
        model.addAttribute("tokenRefillIntervalSeconds", TokenBucketConfig.REFILL_INTERVAL_SECONDS);
        model.addAttribute("leakyCapacity", leakyLimiter.capacity());
        model.addAttribute("leakyLeakIntervalSeconds", LeakyBucketConfig.LEAK_INTERVAL_SECONDS);
        return "index";
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL. (`index.html`은 다음 태스크에서 새 모델 키를 쓰도록 수정 — 이 시점에는 기존 `${capacity}` 참조가 깨져 있을 수 있으나 다음 태스크에서 함께 해결됨.)

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m ":sparkles: HomeController에 토큰/누출 모델 변수 모두 주입"
```

---

## Task 12: index.html — 탭 UI + 누출 패널 + SSE 일원화

**Files:**
- Modify: `src/main/resources/templates/index.html`

- [ ] **Step 1: index.html 전체 교체**

Overwrite `04_처리율제한장치설계/rate-limiter-demo/src/main/resources/templates/index.html`:

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8" />
    <title>Rate Limiter Demo</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; max-width: 720px; margin: 32px auto; padding: 0 16px; color: #1f2328; }
        h1 { margin: 0 0 4px; }
        .status { display: inline-block; margin-left: 12px; padding: 2px 8px; border-radius: 12px; font-size: 12px; }
        .status.connected { background: #dafbe1; color: #1a7f37; }
        .status.disconnected { background: #ffebe9; color: #cf222e; }

        .tabs { display: flex; gap: 4px; border-bottom: 1px solid #d0d7de; margin-top: 24px; }
        .tab { padding: 8px 16px; cursor: pointer; border: 1px solid transparent; border-bottom: none; border-radius: 6px 6px 0 0; color: #57606a; }
        .tab.active { background: #fff; border-color: #d0d7de; color: #1f2328; font-weight: 600; margin-bottom: -1px; }

        .panel { display: none; padding-top: 20px; }
        .panel.active { display: block; }
        .meta { color: #57606a; font-size: 14px; margin-bottom: 24px; }

        /* Token Bucket gauge (horizontal) */
        .gauge-h { display: flex; gap: 12px; margin: 24px 0; }
        .token { width: 48px; height: 48px; border-radius: 50%; background: #d0d7de; transition: background 0.15s; }
        .token.filled { background: #1a7f37; }

        /* Leaky Bucket gauge (vertical water tank) */
        .tank-wrap { display: flex; flex-direction: column; align-items: center; margin: 24px 0; }
        .tank { display: flex; flex-direction: column-reverse; width: 80px; height: 360px; border: 2px solid #57606a; border-radius: 4px 4px 8px 8px; overflow: hidden; background: #f6f8fa; }
        .tank .cell { flex: 1; border-top: 1px solid #eaeef2; transition: background 0.2s; }
        .tank .cell.filled { background: #0969da; }
        .tank.full .cell.filled:nth-last-child(-n+2) { animation: pulse-red 0.8s infinite; }
        @keyframes pulse-red { 0%,100% { background: #0969da; } 50% { background: #cf222e; } }
        .nozzle { width: 16px; height: 16px; border-left: 8px solid transparent; border-right: 8px solid transparent; border-top: 12px solid #57606a; }
        .drip { font-size: 22px; height: 28px; opacity: 0; transition: opacity 0.15s; }
        .drip.show { opacity: 1; }

        button { padding: 10px 20px; font-size: 16px; background: #0969da; color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
        button:active { background: #0860c1; }

        .log { margin-top: 32px; border-top: 1px solid #d0d7de; padding-top: 16px; font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 13px; max-height: 320px; overflow-y: auto; }
        .log .row { padding: 4px 0; border-bottom: 1px solid #f6f8fa; }
        .log .ok { color: #1a7f37; }
        .log .ko { color: #cf222e; }
        .log .info { color: #0969da; }
        .log .ts { color: #57606a; margin-right: 8px; }
        .log .reqid { color: #6e7781; }
        .log .row.highlight { background: #fff8c5; }
    </style>
</head>
<body>
<h1>Rate Limiter Demo
    <span id="status" class="status disconnected">disconnected</span>
</h1>

<div class="tabs">
    <div class="tab active" data-tab="token">Token Bucket</div>
    <div class="tab" data-tab="leaky">Leaky Bucket</div>
</div>

<!-- Token Bucket panel -->
<div class="panel active" data-panel="token">
    <div class="meta">
        capacity = <span th:text="${tokenCapacity}">5</span>
        · refill: 1 token / <span th:text="${tokenRefillIntervalSeconds}">10</span>s
    </div>
    <div id="token-gauge" class="gauge-h" th:attr="data-capacity=${tokenCapacity}"></div>
    <button id="token-btn" type="button">Call /api/token/ping</button>
    <div class="log" id="token-log"></div>
</div>

<!-- Leaky Bucket panel -->
<div class="panel" data-panel="leaky">
    <div class="meta">
        capacity = <span th:text="${leakyCapacity}">15</span>
        · leak rate: 1 req / <span th:text="${leakyLeakIntervalSeconds}">3</span>s
    </div>
    <div class="tank-wrap">
        <div id="leaky-tank" class="tank" th:attr="data-capacity=${leakyCapacity}"></div>
        <div class="nozzle"></div>
        <div id="leaky-drip" class="drip">💧</div>
    </div>
    <button id="leaky-btn" type="button">Call /api/leaky/ping</button>
    <div class="log" id="leaky-log"></div>
</div>

<script th:inline="javascript">
    /*<![CDATA[*/
    const TOKEN_CAPACITY = /*[[${tokenCapacity}]]*/ 5;
    const LEAKY_CAPACITY = /*[[${leakyCapacity}]]*/ 15;
    /*]]>*/

    // ------- 탭 전환 -------
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const name = tab.dataset.tab;
            document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t === tab));
            document.querySelectorAll('.panel').forEach(p =>
                p.classList.toggle('active', p.dataset.panel === name));
        });
    });

    // ------- 연결 상태 -------
    const statusEl = document.getElementById('status');
    const connStatus = { token: false, leaky: false };
    function refreshStatus() {
        const all = connStatus.token && connStatus.leaky;
        statusEl.className = 'status ' + (all ? 'connected' : 'disconnected');
        statusEl.textContent = all ? 'connected' : 'disconnected';
    }

    // ------- 시각 유틸 -------
    function ts() {
        const d = new Date();
        return String(d.getHours()).padStart(2, '0') + ':' +
               String(d.getMinutes()).padStart(2, '0') + ':' +
               String(d.getSeconds()).padStart(2, '0') + '.' +
               String(d.getMilliseconds()).padStart(3, '0');
    }

    function appendRow(logEl, html, reqId) {
        const row = document.createElement('div');
        row.className = 'row';
        if (reqId) row.dataset.reqid = reqId;
        row.innerHTML = '<span class="ts">' + ts() + '</span>' + html;
        if (reqId) {
            row.addEventListener('mouseenter', () => highlightReqId(logEl, reqId, true));
            row.addEventListener('mouseleave', () => highlightReqId(logEl, reqId, false));
        }
        logEl.prepend(row);
    }

    function highlightReqId(logEl, reqId, on) {
        logEl.querySelectorAll('.row[data-reqid="' + reqId + '"]')
            .forEach(r => r.classList.toggle('highlight', on));
    }

    // ------- Token Bucket -------
    const tokenGaugeEl = document.getElementById('token-gauge');
    const tokenLogEl = document.getElementById('token-log');
    const tokenBtnEl = document.getElementById('token-btn');

    for (let i = 0; i < TOKEN_CAPACITY; i++) {
        const dot = document.createElement('div');
        dot.className = 'token';
        tokenGaugeEl.appendChild(dot);
    }
    const tokenDots = tokenGaugeEl.querySelectorAll('.token');

    function renderTokens(n) {
        tokenDots.forEach((dot, i) => dot.classList.toggle('filled', i < n));
    }

    const tokenSse = new EventSource('/api/token/stream');
    tokenSse.addEventListener('open', () => { connStatus.token = true; refreshStatus(); });
    tokenSse.addEventListener('error', () => { connStatus.token = false; refreshStatus(); });

    tokenSse.addEventListener('init', e => {
        const d = JSON.parse(e.data);
        renderTokens(d.tokens);
    });
    tokenSse.addEventListener('consume', e => {
        const d = JSON.parse(e.data);
        renderTokens(d.tokens);
        appendRow(tokenLogEl, '<span class="ok">200 OK</span>  tokens=' + d.tokens);
    });
    tokenSse.addEventListener('refill', e => {
        const d = JSON.parse(e.data);
        renderTokens(d.tokens);
        appendRow(tokenLogEl, '<span class="info">↻ Refill</span>  tokens=' + d.tokens);
    });
    tokenSse.addEventListener('rejected', e => {
        const d = JSON.parse(e.data);
        renderTokens(d.tokens);
        appendRow(tokenLogEl, '<span class="ko">429 Rejected</span>  tokens=' + d.tokens);
    });

    tokenBtnEl.addEventListener('click', () => {
        fetch('/api/token/ping').catch(err => console.warn('token fetch failed', err));
    });

    // ------- Leaky Bucket -------
    const leakyTankEl = document.getElementById('leaky-tank');
    const leakyDripEl = document.getElementById('leaky-drip');
    const leakyLogEl = document.getElementById('leaky-log');
    const leakyBtnEl = document.getElementById('leaky-btn');

    for (let i = 0; i < LEAKY_CAPACITY; i++) {
        const cell = document.createElement('div');
        cell.className = 'cell';
        leakyTankEl.appendChild(cell);
    }
    const leakyCells = leakyTankEl.querySelectorAll('.cell');

    function renderLeaky(size) {
        leakyCells.forEach((c, i) => c.classList.toggle('filled', i < size));
        leakyTankEl.classList.toggle('full', size >= LEAKY_CAPACITY);
    }

    function showDrip() {
        leakyDripEl.classList.add('show');
        setTimeout(() => leakyDripEl.classList.remove('show'), 500);
    }

    const leakySse = new EventSource('/api/leaky/stream');
    leakySse.addEventListener('open', () => { connStatus.leaky = true; refreshStatus(); });
    leakySse.addEventListener('error', () => { connStatus.leaky = false; refreshStatus(); });

    leakySse.addEventListener('init', e => {
        const d = JSON.parse(e.data);
        renderLeaky(d.size);
    });
    leakySse.addEventListener('accepted', e => {
        const d = JSON.parse(e.data);
        renderLeaky(d.size);
        appendRow(leakyLogEl,
            '<span class="ok">202 Accepted</span>  ' +
            '<span class="reqid">reqId=' + d.reqId + '</span>  size=' + d.size + '/' + LEAKY_CAPACITY,
            d.reqId);
    });
    leakySse.addEventListener('processed', e => {
        const d = JSON.parse(e.data);
        renderLeaky(d.size);
        showDrip();
        appendRow(leakyLogEl,
            '<span class="info">✓ Processed</span>  ' +
            '<span class="reqid">reqId=' + d.reqId + '</span>  size=' + d.size + '/' + LEAKY_CAPACITY,
            d.reqId);
    });
    leakySse.addEventListener('rejected', e => {
        const d = JSON.parse(e.data);
        renderLeaky(d.size);
        appendRow(leakyLogEl,
            '<span class="ko">429 Rejected</span>  size=' + d.size + '/' + LEAKY_CAPACITY + '  queue full');
    });

    leakyBtnEl.addEventListener('click', () => {
        fetch('/api/leaky/ping').catch(err => console.warn('leaky fetch failed', err));
    });
</script>
</body>
</html>
```

핵심 설계:
- 탭 두 개. CSS class 토글로 패널 전환.
- 두 SSE 스트림을 진입 시 모두 구독, `connected`는 둘 다 OK일 때만.
- **로그·게이지 모두 SSE에서 처리** (fetch는 네트워크 오류만 콘솔). 토큰 버킷도 동일 패턴으로 통일.
- 같은 `reqId`의 두 row(accepted/processed) 호버 시 양쪽 highlight.

- [ ] **Step 2: 빌드 + 수동 검증**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew bootRun
```

브라우저 `http://localhost:8080` 열고 다음 시나리오를 직접 확인:

1. 페이지 로드 시 우상단 `connected` 상태가 됨.
2. **Token Bucket 탭** (기본): 게이지 5칸 모두 초록. 버튼 5번 빠르게 → 5번 200 OK 로그, 게이지 0. 6번째 → 429. 10초 기다리면 ↻ Refill 로그 + 게이지 1칸 복구.
3. **Leaky Bucket 탭**: 워터탱크 비어있음. 버튼 빠르게 5번 → 5칸 차오름, 5개 `202 Accepted reqId=...` 로그. 3초마다 💧 + ✓ Processed 로그(같은 reqId). 15번까지 채우면 16번째에 `429 Rejected queue full`.
4. **reqId 호버**: accepted 로그에 마우스 올리면 같은 reqId의 processed 로그도 노란 배경으로 highlight.
5. 탭 전환 후 다시 돌아와도 게이지 상태가 유지됨.

`Ctrl+C`로 종료.

- [ ] **Step 3: 커밋**

```bash
cd /home/sajacaros/workspace/system-design
git add -A
git commit -m ":sparkles: 데모 UI 탭 + 누출 버킷 패널 추가, 로그를 SSE 일원화

- 토큰/누출 두 알고리즘을 한 페이지에서 탭 전환으로 비교
- 누출 버킷: 세로 워터탱크 + 누수구(💧) + reqId 호버 매칭
- 두 패널 모두 로그·게이지를 SSE에서 처리 (토큰 버킷도 통일)"
```

---

## Task 13: README 갱신

**Files:**
- Modify: `04_처리율제한장치설계/rate-limiter-demo/README.md`

- [ ] **Step 1: README 전면 교체**

Overwrite `04_처리율제한장치설계/rate-limiter-demo/README.md`:

```markdown
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
```

- [ ] **Step 2: 커밋**

```bash
cd /home/sajacaros/workspace/system-design
git add -A
git commit -m ":books: README 갱신: 토큰/누출 버킷 비교 표 + 동작 흐름"
```

---

## Task 14: 최종 검증 (전체 회귀)

- [ ] **Step 1: 클린 빌드 + 모든 테스트**

```bash
cd /home/sajacaros/workspace/system-design/04_처리율제한장치설계/rate-limiter-demo
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. `TokenBucketTest` 3건 + `LeakyBucketTest` 4건 = **7 tests passed**.

- [ ] **Step 2: bootRun + 통합 시나리오 (수동)**

```bash
./gradlew bootRun
```

브라우저에서:
1. 토큰 버킷 5번 연타 → 5×200 OK + 1×429
2. 누출 버킷 16번 연타 → 15×202 + 1×429 (queue full)
3. 30초 대기 → 누출 큐가 10개 빠지며 💧 애니메이션, processed 로그 10건
4. 토큰 버킷 ↻ Refill 로그 3건 (30초 / 10s)
5. 콘솔 에러 없음 (Network 탭에서 SSE 연결 둘 다 유지)

문제 없으면 `Ctrl+C`. 문제 있으면 해당 태스크로 돌아가 수정 후 다시 검증.

- [ ] **Step 3: git log 정리 확인**

```bash
git log --oneline -15
```

Expected: 13~14개의 작은 커밋이 task 순서대로 쌓여 있음.

---

## Notes

- 모든 `git mv`는 디렉토리 단위 1회로 끝나므로 IDE 인덱싱 영향이 작다.
- `TokenBucket`, `RateLimiter`, `RateLimitFilter`의 패키지 경로는 변경하지 않으므로 `TokenBucketTest`도 import 수정 불필요.
- `LeakyBucket`은 `LinkedBlockingDeque(capacity)`를 사용 — `offer()`는 가득 차면 즉시 false 반환, `poll()`은 head에서 즉시 반환. 단일 누수 스레드 + 다중 enqueue 스레드(서블릿)에 대해 스레드 안전.
- Filter가 직접 응답을 작성하므로 `/api/leaky/ping`에 대응하는 `@Controller`/`@RestController`는 만들지 않는다.
