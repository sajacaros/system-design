# Monitoring Demo — Spring Boot + Prometheus + Grafana

이 폴더는 1장 "사용자 수에 따른 규모 확장성"의 **로그·매트릭·자동화** 절을 직접 손으로 체험해 보기 위한 작은 실습 프로젝트입니다.
API를 호출해 부하를 만들고, Prometheus가 그 부하를 메트릭으로 수집하고, Grafana가 그래프로 보여 줍니다.

> **이 README의 대상**: Prometheus·Grafana가 무엇인지 들어 본 적은 있지만 직접 써 본 적은 없는 분.
> 따라서 각 도구의 역할과 동작 방식을 처음부터 차근차근 설명합니다.

---

## 1. 등장 인물 한 줄 정리

| 도구 | 역할 |
|---|---|
| **Spring Boot 앱** | 우리가 모니터링하고 싶은 대상 (이 프로젝트에서는 부하를 만드는 작은 API 서버) |
| **Micrometer + Actuator** | Spring Boot 안에서 메트릭을 수집해 `/actuator/prometheus` 경로로 노출 |
| **Prometheus** | 위 경로를 정해진 주기로 스크랩(scrape)해서 시계열 데이터로 저장 |
| **Grafana** | Prometheus에 질의(query)해서 그래프·대시보드로 시각화 |

흐름:
```
[ Spring Boot ] --(메트릭 노출)-->  /actuator/prometheus
                                        ▲
                                        │ 5초마다 HTTP GET
                                        │
                                  [ Prometheus ] --(저장)--> 시계열 DB
                                        ▲
                                        │ PromQL 쿼리
                                        │
                                   [ Grafana ] --(렌더링)--> 사람이 보는 그래프
```

---

## 2. 각 도구가 정확히 뭘 하나?

### Prometheus

- **무엇**: 시계열(time-series) 메트릭을 수집·저장·질의하는 도구.
- **수집 방식**: **풀(pull) 모델** — Prometheus가 대상 서비스의 HTTP 엔드포인트(`/metrics` 또는 임의 경로)에 주기적으로 요청을 보내 응답으로 받은 텍스트를 파싱해 저장합니다.
  앱이 Prometheus에 데이터를 "보내는" 게 아니라, Prometheus가 앱에서 "긁어 가는" 구조라는 점이 가장 중요합니다.
- **데이터 모델**: 모든 데이터가 `메트릭_이름{라벨1="값", 라벨2="값"} 숫자값` 형식의 시계열로 저장됩니다.
  예: `http_server_requests_seconds_count{uri="/load/cpu", method="GET", status="200"} 42`
- **질의 언어**: PromQL. 위 시계열에 대해 `rate()`, `sum()`, `histogram_quantile()` 같은 함수를 조합해 그래프에 그릴 값을 계산합니다.
- **이 프로젝트에서**: `prometheus/prometheus.yml`에 정의된 대로 5초마다 `app:8080/actuator/prometheus`를 스크랩합니다.

### Grafana

- **무엇**: 시계열 데이터를 시각화하는 대시보드 도구. 자체 저장소는 없고, Prometheus 같은 **데이터소스**에 PromQL 쿼리를 보내서 그래프를 그립니다.
- **이 프로젝트에서**: `grafana/provisioning/`을 통해 시작 시 자동으로
  1. Prometheus 데이터소스를 등록하고,
  2. `grafana/dashboards/monitoring-demo.json` 대시보드를 불러옵니다.
  덕분에 Grafana UI에서 데이터소스를 직접 추가하지 않아도 바로 그래프가 보입니다.

### Spring Boot Actuator + Micrometer

- **Actuator**: Spring Boot가 운영용 엔드포인트(`/actuator/health`, `/actuator/metrics` 등)를 제공해 주는 모듈.
- **Micrometer**: "메트릭의 SLF4J"라고 비유되는 라이브러리. 코드는 Micrometer API에 의존하고, Prometheus·Datadog·CloudWatch 등 어디로 내보낼지는 의존성 추가만으로 결정됩니다. 이 프로젝트는 `micrometer-registry-prometheus`를 추가했기 때문에 `/actuator/prometheus` 경로가 활성화됩니다.

---

## 3. 빠른 시작

### 사전 요구
- Docker / Docker Compose 만 있으면 됩니다 (Java·Gradle 별도 설치 불필요. 빌드는 Docker 빌드 단계에서 수행).

### 실행
```bash
cd 01_사용자수에따른규모확장성/monitoring-demo
docker compose up -d --build
```

처음 실행할 때 Spring Boot 앱을 컨테이너 안에서 빌드하느라 1~3분 걸릴 수 있습니다.

### 접속
| 무엇 | URL | 비고 |
|---|---|---|
| Spring Boot 앱 | http://localhost:8080 | 부하 엔드포인트가 여기 있음 |
| Actuator 헬스체크 | http://localhost:8080/actuator/health | `{"status":"UP"}` 가 떠야 정상 |
| 메트릭 원본(Prometheus 포맷) | http://localhost:8080/actuator/prometheus | 스크롤하면 모든 메트릭이 텍스트로 보임 |
| Prometheus UI | http://localhost:9090 | `Status > Targets`에서 `spring-boot-app` 이 `UP` 인지 확인 |
| Grafana | http://localhost:3000 | 로그인: `admin` / `admin` |

### 종료
```bash
docker compose down
```

---

## 4. 부하를 만들어 보고 그래프에서 확인하기

세 가지 엔드포인트로 CPU·메모리 부하를 만들 수 있습니다.

### CPU 부하
```bash
# 4개 스레드가 30초간 빈 루프로 CPU를 점유
curl "http://localhost:8080/load/cpu?seconds=30&threads=4"
```
- **확인할 패널**: Grafana의 **Process CPU Usage (%)**
- 호출 직후 값이 0.0x → 점점 위로 솟구치며 (스레드 수 / 코어 수) 비율 근처까지 올라갔다가, 30초 뒤 다시 떨어집니다.
- 동시에 **JVM Threads** 패널에서 살아 있는 스레드 수가 `threads` 만큼 늘어났다가 줄어드는 것도 보입니다.

### 메모리 부하
```bash
# 200MB를 60초간 잡아 두고, 그 후 자동 해제
curl -X POST "http://localhost:8080/load/memory?mb=200&seconds=60"
```
- **확인할 패널**: Grafana의 **JVM Heap Memory (MB)**
- `heap used` 라인이 호출 시점에 200MB 점프 → 60초 뒤 GC가 돌면서 떨어짐.
- `heap committed` 는 JVM이 OS로부터 받아 둔 용량으로, used가 늘어나면서 같이 올라가지만 줄어드는 시점은 한 박자 늦습니다 (JVM이 OS에 메모리를 바로 반납하지 않음).
- `heap max` 는 도커 환경변수 `JAVA_OPTS=-Xmx512m`로 설정한 상한선이라 평평한 직선으로 보입니다.

### 메모리 강제 해제
```bash
curl -X POST "http://localhost:8080/load/memory/release"
```
- 모든 보유 블록을 `clear()`하고 `System.gc()`를 호출. heap used가 즉시 떨어지는 모습을 볼 수 있습니다.

### 안전장치
컨트롤러에 클램프(clamp) 로직이 있어 다음 한도를 넘는 요청은 자동으로 한도까지 줄여서 처리합니다 (서버를 죽이지 않기 위해).
- CPU: 최대 32 스레드, 120초
- 메모리: 최대 1024 MB, 600초 보유

---

## 5. Grafana 대시보드 사용법

처음 접속하면 좌측 사이드바 → 대시보드(네 개의 사각형 아이콘) → **Monitoring Demo — JVM & HTTP**.

| 패널 | 보이는 것 | 사용된 PromQL (요지) |
|---|---|---|
| Process CPU Usage (%) | JVM 프로세스와 시스템 전체 CPU 사용률 | `process_cpu_usage`, `system_cpu_usage` |
| JVM Heap Memory (MB) | 힙 사용량 / 커밋 / 최대치 | `sum(jvm_memory_used_bytes{area="heap"})` 등 |
| JVM Threads | 살아있는 스레드 수 | `jvm_threads_live_threads` |
| HTTP Request Rate (req/s) | 엔드포인트별 초당 요청 수 (1분 평균) | `rate(http_server_requests_seconds_count[1m])` |
| HTTP Latency p95 (s) | 엔드포인트별 95퍼센타일 응답 시간 | `histogram_quantile(0.95, ...)` |

**대시보드 새로고침 주기**: 우측 상단 시계 옆 드롭다운에서 변경 가능. 기본은 5초입니다.

**시간 범위**: 우측 상단 "Last 15 minutes" 부분을 클릭하면 변경할 수 있습니다. 부하 테스트가 짧으면 "Last 5 minutes" 정도가 보기 좋습니다.

### 더 풍부한 대시보드를 보고 싶다면
Grafana 좌측 메뉴 → 대시보드 → **New > Import** → ID 입력란에 **`4701`** (JVM Micrometer) 또는 **`12900`** (Spring Boot Statistics) 을 넣고 Load → 데이터소스로 `Prometheus` 선택.
공식 커뮤니티 대시보드를 그대로 붙여 쓸 수 있습니다.

---

## 6. Prometheus UI에서 직접 PromQL 쳐 보기

http://localhost:9090 → 상단 검색 박스에 PromQL을 입력하고 **Execute**. 시각화 탭(Graph)을 누르면 그래프로도 볼 수 있습니다.

추천 쿼리:
```promql
# 분당 요청 수 — 엔드포인트별
sum by (uri) (rate(http_server_requests_seconds_count[1m]))

# 힙 메모리 사용량을 영역별로
jvm_memory_used_bytes{area="heap"}

# GC 발생 횟수 (누적)
jvm_gc_pause_seconds_count

# CPU 사용률
process_cpu_usage

# 스레드 상태별 카운트 (사실 이런 식의 라벨 탐색을 해보기 좋습니다)
jvm_threads_states_threads
```

`Status > Targets` 메뉴에서 Prometheus가 우리 앱을 얼마나 잘 스크랩하고 있는지 (`UP`/`DOWN`, last scrape duration 등) 확인할 수 있습니다.

---

## 7. 디렉토리 구조

```
monitoring-demo/
├── build.gradle, settings.gradle           # Gradle 빌드 정의 (Java 17, Spring Boot 3.4)
├── Dockerfile                              # 멀티스테이지: gradle 빌드 → temurin JRE 실행
├── .dockerignore
├── docker-compose.yml                      # app + prometheus + grafana
├── prometheus/
│   └── prometheus.yml                      # 스크랩 설정 (5초 주기로 app:8080)
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml      # 데이터소스 자동 등록
│   │   └── dashboards/default.yml          # 대시보드 폴더 프로비저너
│   └── dashboards/
│       └── monitoring-demo.json            # 시작용 커스텀 대시보드
├── src/main/java/kr/study/monitoring/
│   ├── MonitoringApplication.java
│   └── controller/LoadController.java      # /load/cpu, /load/memory, /load/memory/release
└── src/main/resources/application.yml      # Actuator/Prometheus 노출 설정
```

---

## 8. 트러블슈팅

### Grafana에서 "No data" 만 떠요
1. Prometheus 자체는 잘 떠 있나요? → http://localhost:9090
2. Prometheus가 앱을 잘 스크랩하나요? → http://localhost:9090/targets 에서 `spring-boot-app` 이 `UP`인지 확인.
   - `DOWN` 이면 보통 앱 컨테이너가 아직 기동 중이거나, 헬스체크 없이 Prometheus가 먼저 떠서 첫 스크랩에 실패한 경우입니다. 30초~1분 기다린 뒤 새로고침해 보세요.
3. 메트릭이 실제로 나오나요? → http://localhost:8080/actuator/prometheus (`# HELP`, `# TYPE` 으로 시작하는 텍스트가 와야 정상)
4. 그래도 안 나오면: 시간 범위가 너무 좁거나 (우측 상단), 부하 호출 자체를 안 했을 수도 있습니다. CPU/메모리 부하를 한 번 더 호출해 보세요.

### `docker compose up` 에서 빌드가 너무 느려요
Gradle이 첫 빌드에서 의존성을 모두 받아오느라 느립니다. 두 번째부터는 Docker 레이어 캐시 덕에 훨씬 빨라집니다.

### 포트가 이미 점유돼 있어요
`docker-compose.yml`의 `ports` 섹션에서 호스트 쪽 포트만 바꿔 주세요. 예: `"18080:8080"`.

### Grafana 비밀번호를 잊었어요
이 데모는 단순화를 위해 `admin/admin`으로 고정. 바꾸려면 `docker-compose.yml`의 `GF_SECURITY_ADMIN_PASSWORD` 값을 수정 후 `docker compose up -d --force-recreate grafana`.

---

## 9. 더 알아보기 (다음 단계)

- **로그 + 메트릭 + 트레이스의 삼각형**: 메트릭은 "무엇이 문제인가"는 알려 주지만 "왜인가"는 알려 주지 않습니다. 다음 단계는 분산 트레이싱(예: OpenTelemetry → Jaeger/Tempo)을 붙여 보는 것.
- **알람**: Prometheus의 `alerting` 섹션에서 임계치를 정의하고 Alertmanager로 슬랙·이메일 알림을 보낼 수 있습니다.
- **고가용성**: 단일 Prometheus는 SPOF입니다. 실서비스에서는 Thanos/Mimir/Cortex 등을 통해 장기 저장과 복제를 같이 다룹니다.
- **메트릭 이름 규칙**: Prometheus 공식 가이드의 [Naming](https://prometheus.io/docs/practices/naming/) 문서를 한 번 읽어 보세요.

---

## 10. 메트릭 사전 (이 데모에서 자주 보게 되는 것들)

| 메트릭 | 의미 |
|---|---|
| `process_cpu_usage` | 이 JVM 프로세스의 CPU 사용률 (0~1 사이의 비율) |
| `system_cpu_usage` | 시스템 전체 CPU 사용률 (0~1 사이의 비율) |
| `jvm_memory_used_bytes{area="heap"}` | 힙 메모리에서 실제 사용 중인 바이트 |
| `jvm_memory_committed_bytes{area="heap"}` | JVM이 OS로부터 받아 둔 힙 메모리 |
| `jvm_memory_max_bytes{area="heap"}` | 힙 메모리 상한 (`-Xmx`) |
| `jvm_gc_pause_seconds_*` | GC로 인한 정지 시간 분포 (count, sum, bucket) |
| `jvm_threads_live_threads` | 현재 살아 있는 스레드 수 |
| `jvm_threads_states_threads{state="..."}` | 상태(`runnable`, `blocked` 등)별 스레드 수 |
| `http_server_requests_seconds_count` | HTTP 요청 누적 카운터 (라벨로 uri, method, status) |
| `http_server_requests_seconds_bucket` | 응답 시간 히스토그램 — `histogram_quantile`로 p50/p95/p99 계산 |

`_count` / `_sum` / `_bucket` 접미사가 붙은 메트릭은 Micrometer가 만든 **히스토그램 타입**의 일부입니다. 단독으로는 의미가 적고 보통 `rate()` + `histogram_quantile()` 조합으로 사용합니다.
