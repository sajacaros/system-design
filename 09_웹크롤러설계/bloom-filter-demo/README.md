# Bloom Filter Demo

## 문서

- Bloom filter 원리 설명: `docs/bloomFilter.html`

9장 웹 크롤러 설계에서 방문한 URL 중복 검사용으로 언급되는 Bloom filter를 직접 확인하는 Spring Boot 데모입니다.

## 무엇을 볼 수 있나

- URL 하나가 여러 해시 함수로 여러 비트 위치에 매핑되는 과정
- 조회 시 하나라도 0인 비트가 있으면 "확실히 방문하지 않음"이라고 말할 수 있는 이유
- 모든 비트가 1이어도 실제로는 방문하지 않았을 수 있는 false positive
- 비트 배열 크기와 해시 함수 수에 따라 false positive 확률이 달라지는 모습

## 실행

```bash
cd 09_웹크롤러설계/bloom-filter-demo
./gradlew bootRun
```

Windows PowerShell에서는 다음처럼 실행할 수 있습니다.

```powershell
cd 09_웹크롤러설계\bloom-filter-demo
.\gradlew.bat bootRun
```

작업공간을 `\\wsl.localhost\...` 경로로 열어 둔 경우에는 Windows `cmd.exe`가 UNC 현재 디렉터리를 지원하지 않으므로 WSL 터미널에서 `./gradlew bootRun`으로 실행하는 편이 안전합니다.

브라우저에서 http://localhost:7909 를 엽니다.

## 테스트

```bash
./gradlew test
```

## 핵심 아이디어

Bloom filter는 실제 URL 문자열을 모두 저장하지 않고 비트 배열만 저장합니다. URL을 추가할 때 여러 해시 함수가 가리키는 비트를 1로 바꾸고, 조회할 때 같은 위치들이 모두 1인지 확인합니다.

- 0이 하나라도 있으면 그 URL은 확실히 없었습니다.
- 모두 1이면 아마 있었던 URL입니다.
- "아마"인 이유는 다른 URL들이 우연히 같은 비트들을 켜 두었을 수 있기 때문입니다.
