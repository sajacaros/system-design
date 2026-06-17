## 모듈: 공통 기반 (scaffold + common + security + storage)

## 구현 범위
- Gradle 스캐폴드: `build.gradle`(Spring Boot 4.0.7, Java 25 toolchain, AWS SDK v2 S3, jjwt 0.12.6, WebSocket), `settings.gradle`(foojay toolchain resolver 1.0.0), Gradle wrapper 9.5.1, `gradle.properties`(toolchain auto-download).
- `application.yml`: DB/MinIO/JWT 전부 환경변수 주입(`${...:default}`). 멀티파트 최대 100MB(환경변수 조정).
- 멀티스테이지 `Dockerfile`: `eclipse-temurin:25-jdk` 빌드 → `25-jre` 런타임, non-root, compose가 `./backend` 빌드.
- 엔티티 7종(JPA): `users`, `refresh_token`, `folder`, `file`, `file_version`, `share_link`, `notification` + `BaseEntity`(id IDENTITY, created_at/updated_at auditing, UTC).
- 전역 예외 핸들러: 계약 에러 바디 `{code,message,timestamp}` + 코드 enum(VALIDATION/UNAUTHENTICATED/FORBIDDEN/NOT_FOUND/CONFLICT/PAYLOAD_TOO_LARGE + EMAIL_TAKEN/BAD_CREDENTIALS/INVALID_REFRESH/NAME_CONFLICT/CYCLIC_MOVE/FILE_NOT_FOUND/INVALID_LINK/EXPIRED/DISABLED).
- JWT 필터 + SecurityConfig: stateless, Bearer 검증, permitAll(signup/login/refresh, /share/**, /ws/**), 401/403 JSON 응답.
- StorageService 인터페이스 + S3StorageService(AWS SDK v2 S3Client, path-style, presign GET, copy, deletePrefix). 기동 시 버킷 생성(미기동 시 경고 후 진행). ObjectKeys 헬퍼.

## 계약 대조
- [x] 에러 바디/코드 계약 일치
- [x] 인증/인가 기반(JWT, owner 검증 헬퍼)
- [x] 스토리지 S3 추상화 + path-style + presign

## 빌드·테스트
- 명령: `./gradlew compileJava` / `./gradlew test` / `./gradlew bootJar`
- 결과: compile 통과, bootJar 생성, 전체 테스트 통과 14/14.
- 환경: Java 25 toolchain은 Gradle foojay resolver로 자동 다운로드(로컬 JDK는 17). Spring Boot 4.0.7은 Maven Central에서 해석.

## 확인 필요
- DB 테이블명: 계약은 `user`이나 PostgreSQL/H2 예약어라 엔티티는 `users`로 매핑. 계약 의도상 단일 사용자 테이블이라 동작 동일. 설계자 확인 권장(스키마 문서 표기 vs 실제 테이블명).
- Spring Boot 4.x는 Jackson 3(`tools.jackson.*`) 사용. JSON 직렬화 동작은 동일(필드명 동일), 응답 형태 영향 없음.
