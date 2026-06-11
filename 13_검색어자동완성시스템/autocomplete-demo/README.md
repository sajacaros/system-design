# Autocomplete Demo

13장 `검색어 자동완성 시스템`의 트라이 조회 흐름을 확인하는 Spring Boot 데모입니다.

## 비교 대상

- 직접 구현: 한글 자모/초성 키를 함께 색인하고, 트라이 노드마다 top-K 후보를 캐시합니다.
- 라이브러리 구현: `io.github.crizin:korean-utils:0.0.1`의 `KoreanUtils.startsWith`로 한글 부분 입력을 판정합니다.

## 실행

```bash
cd 13_검색어자동완성시스템/autocomplete-demo
./gradlew bootRun
```

브라우저에서 `http://localhost:7913`를 엽니다.

Windows에서 JDK가 한글 경로를 깨진 `user.dir`로 읽는 환경이면 PowerShell에서 아래 명령을 사용합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\run-demo.ps1
```

## 테스트

```bash
./gradlew test
```

Windows 한글 경로 문제가 있으면 아래 명령을 사용합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\test-demo.ps1
```
