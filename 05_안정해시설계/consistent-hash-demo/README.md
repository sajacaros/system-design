# Consistent Hash Demo

05장 `안정 해시 설계`의 핵심 동작을 확인하는 Spring Boot 데모입니다.

## 포함 시나리오

- `Modulo Hash`: `hash(key) % N` 방식에서 서버 수가 바뀌면 많은 키가 재배치되는 문제
- `Hash Ring`: 키 위치에서 시계 방향으로 이동해 처음 만나는 서버 토큰에 매핑하는 기본 조회

## 실행

```bash
cd 05_안정해시설계/consistent-hash-demo
./gradlew bootRun
```

브라우저에서 `http://localhost:7999`를 엽니다.

## 테스트

```bash
./gradlew test
```
