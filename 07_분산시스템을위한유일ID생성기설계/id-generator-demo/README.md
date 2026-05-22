# Snowflake 고유 ID 생성기 데모

7장 Snowflake ID 생성 방식을 Spring Boot + Thymeleaf 화면으로 확인하는 데모입니다.

3개의 Spring Boot 서버를 띄우고 각 서버가 서로 다른 `workerId`로 ID를 생성합니다.

- Node A: http://localhost:7707, `datacenterId=1`, `workerId=1`
- Node B: http://localhost:7717, `datacenterId=1`, `workerId=2`
- Node C: http://localhost:7727, `datacenterId=1`, `workerId=3`

브라우저는 보통 Node A인 http://localhost:7707 을 열면 됩니다. 화면에서 "3개 서버에서 생성" 버튼을 누르면 A/B/C에 단건 생성 API를 여러 번 보냅니다. 기본값은 서버당 5개이므로 총 15번의 API 호출이 발생합니다.

각 노드 안에서는 이전 단건 요청의 응답을 받은 뒤 다음 요청을 시작합니다. 응답 직전에는 300~500ms 랜덤 지연을 넣어서 노드별 다음 ID 생성 시작 시점이 자연스럽게 달라집니다. 화면은 이 결과를 요청 완료 순서와 ID 정렬 순서로 나란히 보여줍니다.

## 구현 범위

- 64비트 Snowflake ID 생성
- 비트 배치: sign 1bit, timestamp 41bit, datacenter 5bit, worker 5bit, sequence 12bit
- custom epoch: `2024-01-01T00:00:00Z`
- 같은 millisecond 안에서는 sequence 증가
- sequence가 4095를 넘으면 다음 millisecond까지 대기
- 서버별 `workerId` 분리로 충돌 방지
- ID를 다시 timestamp/datacenter/worker/sequence로 decode
- 3개 노드에 단건 API를 노드별 순차 호출해 생성한 ID를 합쳐 정렬 가능성 확인
- 응답 직전 랜덤 지연으로 완료 순서 변동성 확인

## 실행

먼저 JAR를 빌드한 뒤 Docker Compose를 실행합니다.

```bash
./gradlew bootJar
docker compose up -d --build
```

Windows PowerShell에서는 다음처럼 실행할 수 있습니다.

```powershell
.\gradlew.bat bootJar
docker compose up -d --build
```

상태 확인:

```bash
docker compose ps
```

종료:

```bash
docker compose down
```

## 확인 포인트

1. 각 노드의 `datacenterId`, `workerId`가 다르게 표시되는지 확인합니다.
2. "3개 서버에서 생성"을 눌러 여러 ID를 만듭니다.
3. `ID 정렬 순서` 테이블에서 timestamp가 역행하지 않는지 확인합니다.
4. 같은 millisecond에 여러 노드가 생성하면 timestamp는 같을 수 있고, 그 안에서는 datacenter/worker/sequence 비트가 정렬 순서를 결정합니다.
