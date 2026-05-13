# Consistent Hash Demo

05장 `안정 해시 설계`의 핵심 장면을 인터랙티브하게 확인하는 Spring Boot 데모다.

## 포함된 시나리오

- `Modulo Hash`: `hash(key) % N` 방식에서 서버 수가 변할 때 키가 크게 재배치되는 문제
- `Consistent Hash`: 키가 시계 방향 첫 서버로 매핑되는 기본 조회
- `Node Change`: 서버 추가/삭제 시 일부 키만 이동하는 장면
- `Virtual Nodes`: 가상 노드 수를 늘렸을 때 분포 편차가 줄어드는 비교

## 실행

```bash
cd 05_안정해시설계/consistent-hash-demo
./gradlew bootRun
```

브라우저에서 `http://localhost:8080`을 연다.

## 테스트

```bash
./gradlew test
```
