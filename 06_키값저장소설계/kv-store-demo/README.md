# 분산 키-값 저장소 데모

6장 키-값 저장소 설계 내용을 눈으로 확인하기 위한 Spring Boot 데모입니다.

이 데모는 독립적인 Spring Boot 노드 5개를 Docker Compose로 실행합니다.

- `A`: http://localhost:7906
- `B`: http://localhost:7916
- `C`: http://localhost:7926
- `D`: http://localhost:7936
- `E`: http://localhost:7946

브라우저는 보통 `A` 노드인 http://localhost:7906 으로 열면 됩니다. 화면에서 요청을 처리할 coordinator 노드는 따로 선택할 수 있습니다.

## 구현 범위

현재 데모에 구현된 내용은 다음과 같습니다.

- 어떤 노드든 coordinator로 선택 가능
- SHA-256 기반 consistent hash ring
- physical node당 16개 virtual node
- key별 replica owner 선택
- `Server = 5`
- `N = 3`: key 하나를 저장하는 replica 수
- 기본 quorum `W=2`, `R=2`
- UI에서 `W`, `R` 동적 변경
- write quorum, read quorum 결과 표시
- 벡터 시계 기반 버전 관리
- 벡터 시계 기반 sibling 정리와 conflict 판정
- gossip 기반 membership 전파
- heartbeat 기반 `ALIVE`, `SUSPECT`, `DEAD` 상태
- 노드 `Pause`/`Resume`으로 장애 상황 재현
- replica write 실패 시 pending hint 저장
- 자동/수동 hinted handoff
- 각 노드의 실제 `local store`, pending hints, membership, event log 표시

주의할 점도 있습니다.

- `N`은 전체 서버 수가 아니라 특정 key를 저장하는 replica 수입니다.
- 이 데모의 hinted handoff는 실패한 replica owner에게 나중에 다시 전달할 hint를 coordinator가 보관하는 방식입니다.
- Dynamo 스타일의 full sloppy quorum처럼 임시 non-owner 노드에 대체 저장하는 동작까지 구현하지는 않았습니다.
- conflict는 벡터 시계로 감지하고 siblings로 보여주지만, 사용자가 conflict를 수동 병합하는 UI는 없습니다.

## Hash Ring과 Replica

각 key는 SHA-256 기반 consistent hash ring에 배치됩니다. 기본 설정은 물리 노드 1개당 virtual node 16개이므로, 5개 서버 클러스터에는 총 80개의 ring token이 있습니다.

key 하나가 저장될 노드는 hash ring으로 결정됩니다. 기본 복제 계수는 `N=3`이므로, 전체 5개 노드 중 key마다 3개 노드만 replica owner가 됩니다.

예를 들어 `cart:42`의 replica owner가 `B`, `D`, `A`라면:

```text
PUT cart:42=book
-> B, D, A에만 저장
-> C, E에는 저장하지 않음
```

화면의 `placement` 영역은 현재 입력한 key가 hash ring에서 어느 노드들에 저장되는지 보여줍니다. 각 노드 카드의 `local store`는 그 노드가 실제로 가진 key/value 목록입니다.

## Coordinator

클라이언트 요청은 화면에서 선택한 coordinator 노드로 보낼 수 있습니다.

```text
브라우저 -> A 노드 UI/API -> 선택한 coordinator -> replica owners
```

예를 들어 coordinator를 `C`로 선택하고 `PUT cart:42=book`을 실행하면, `A`가 `C`의 API를 호출하고 `C`가 hash ring으로 replica owner를 계산해 write를 진행합니다.

선택한 coordinator가 중지되어 있으면 현재 요청을 받은 노드가 fallback coordinator로 처리합니다. 단, 요청을 직접 받은 노드 자체가 `Pause` 상태라면 요청은 실패합니다.

## Quorum

기본 설정은 다음과 같습니다.

```text
servers = 5
N = 3 replicas per key
W = 2
R = 2
virtual nodes = 16 per physical node
```

`W`는 write 성공에 필요한 replica ack 수이고, `R`은 read 성공에 필요한 replica 응답 수입니다.

기본값에서는 `W + R > N`이므로 성공한 write quorum과 read quorum이 최소 하나 이상의 replica에서 겹칩니다.

UI에서 `W`와 `R`을 바꿀 수 있습니다.

- `W=2, R=2`: 읽기/쓰기 quorum이 겹치는 균형 설정
- `W=3, R=1`: 읽기는 빠르지만 write가 모든 replica ack를 요구
- `W=1, R=3`: write는 빠르지만 read가 모든 replica를 확인
- `W=2, R=1`: 지연은 낮지만 `W + R = N`이라 stale read 가능

쓰기와 읽기는 quorum 수만큼 처음부터 잘라서 시도하지 않습니다.

- write는 replica owner 전체에 전송하고, 성공 ack 수가 `W` 이상인지 확인합니다.
- read는 replica owner를 순서대로 시도하고, 성공 응답 수가 `R`에 도달하면 멈춥니다.
- 중간 replica가 죽어 있어도 뒤의 replica에서 quorum을 채울 수 있으면 성공합니다.

## Vector Clock

PUT 시 coordinator는 replica owner들의 기존 version clock을 읽어 병합하고, 자기 node id의 counter를 증가시켜 새 version을 만듭니다.

저장 시에는 벡터 시계를 비교합니다.

- 새 version이 기존 version보다 최신이면 기존 version을 제거합니다.
- 기존 version이 새 version보다 최신이면 새 version을 버립니다.
- 서로 선후 관계가 없으면 conflict로 보고 siblings를 유지합니다.

GET 결과에는 version id, value, vector clock, conflict 여부가 표시됩니다.

## Gossip과 Membership

각 노드는 주기적으로 heartbeat를 증가시키고, 임의의 peer 하나와 gossip을 수행합니다.

gossip으로 membership 정보를 교환하며, peer 응답 상태와 마지막 관측 시간을 기준으로 노드 상태를 갱신합니다.

- `ALIVE`: 최근에 정상 응답한 노드
- `SUSPECT`: 일정 시간 동안 응답이 없어 의심 상태인 노드
- `DEAD`: 더 오래 응답이 없어 죽은 것으로 보는 노드

UI의 `membership` 영역에서 각 노드가 알고 있는 membership 상태를 볼 수 있습니다.

## Hinted Handoff

replica write 중 특정 replica owner에 도달하지 못하면 coordinator는 해당 write를 pending hint로 저장합니다.

중지된 노드가 다시 `Resume`되면 hinted handoff가 자동으로 재시도됩니다. 필요하면 `Hinted Handoff 수동 실행` 버튼으로 즉시 재시도할 수도 있습니다.

각 노드 카드의 `pending hints`에서 아직 전달되지 않은 hint 수와 내용을 확인할 수 있습니다.

## 실행 방법

먼저 JAR를 새로 만든 뒤 Docker Compose를 올립니다.

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

## Quorum Overlap 확인

1. 모든 노드를 `AVAILABLE` 상태로 둡니다.
2. key를 `cart:42`로 둡니다.
3. placement 영역에서 `cart:42`의 replica owner 3개를 확인합니다.
4. `PUT cart:42=book`을 실행합니다.
5. replica owner 중 하나를 `Pause`합니다.
6. value를 `book,pen`으로 바꾸고 다시 `PUT`합니다.
7. 기본 설정 `W=2, R=2`에서는 replica 2개의 ack만 받아도 write quorum이 성립합니다.
8. `GET cart:42`를 실행하면 read quorum도 2개 replica를 읽으므로, 성공한 write quorum과 겹치는 replica를 읽게 됩니다.

stale read 위험을 보고 싶다면 preset을 `W=2, R=1`로 바꿔 같은 흐름을 반복합니다. 이 경우 `W + R = N`이라 read quorum이 write quorum과 겹치지 않을 수 있습니다.

## Hinted Handoff 확인

1. `W=2, R=2`로 둡니다.
2. key를 `cart:42`로 둡니다.
3. placement 영역에서 replica owner 3개를 확인합니다.
4. replica owner 중 하나를 `Pause`합니다.
5. `PUT cart:42=book,pen`을 실행합니다.
6. coordinator는 도달하지 못한 replica owner에 전달할 pending hint를 저장합니다.
7. 멈췄던 노드를 `Resume`합니다.
8. 자동 hinted handoff를 기다리거나 `Hinted Handoff 수동 실행` 버튼으로 즉시 재시도합니다.

## 화면에서 볼 것

- `placement`: 현재 입력한 key의 hash 값과 replica owner 목록
- `local store`: 각 노드가 실제로 저장 중인 key/value 목록
- `selected`: 현재 입력한 key가 해당 노드의 local store에 실제로 있을 때 표시
- `pending hints`: 장애 중 전달하지 못해 나중에 재전송할 replica write
- `membership`: gossip/heartbeat 기준으로 본 노드 상태
- `events`: 해당 노드에서 발생한 PUT, GET, gossip, hint 저장/전달 이벤트

key 입력값을 바꾸면 placement는 즉시 새 key 기준으로 다시 계산됩니다. 하지만 `local store`는 실제 저장소 내용이므로, PUT 전에는 새 key가 저장소에 생기지 않습니다.

## 로컬 프로세스로 직접 실행

여러 노드를 로컬 프로세스로 직접 띄우는 것도 가능하지만 환경 변수를 맞춰야 해서 번거롭습니다. 일반적인 확인은 Docker Compose를 권장합니다.

직접 실행하려면 노드마다 다음 값을 다르게 지정해야 합니다.

- `NODE_ID`
- `SERVER_PORT`
- `PEERS`

그리고 모든 노드에서 아래 값은 같은 클러스터 설정으로 맞춥니다.

- `REPLICATION_FACTOR=3`
- `VIRTUAL_NODES=16`
- `WRITE_QUORUM=2`
- `READ_QUORUM=2`
- `SUSPECT_AFTER_MS=5000`
- `DEAD_AFTER_MS=10000`

구체적인 값은 `docker-compose.yml`의 각 서비스 설정을 기준으로 보면 됩니다.
