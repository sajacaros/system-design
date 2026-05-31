# URL Shortener Demo

8장 URL 단축키 설계의 두 가지 단축 URL 생성 방식을 Spring Boot 화면으로 확인하는 데모입니다.

- Hash + collision retry: SHA-256 결과를 7자리 Base62 코드로 줄이고, 충돌 시 salt 값을 바꿔 재시도합니다.
- Snowflake ID + Base62: 7장과 같은 64비트 Snowflake ID를 만든 뒤 Base62 문자열로 변환합니다.
- Redirect: `/r/{code}`는 `302 Found`와 `Location` 헤더를 반환합니다.

## 실행

```powershell
.\gradlew.bat bootRun
```

브라우저에서 `http://localhost:7808`을 엽니다.

Docker Compose로 실행하려면 먼저 JAR를 빌드합니다.

```powershell
.\gradlew.bat bootJar
docker compose up -d --build
```

종료:

```powershell
docker compose down
```

## API

```http
POST /api/shorten
Content-Type: application/json

{
  "longUrl": "https://example.com/articles/1",
  "strategy": "HASH"
}
```

`strategy`는 `HASH` 또는 `ID_BASE62`입니다.

충돌 재현:

```http
POST /api/collision-demo
Content-Type: application/json

{
  "longUrl": "https://example.com/articles/1"
}
```
