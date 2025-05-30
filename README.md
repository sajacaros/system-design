# 대규모 시스템 설계 기초 스터디

이 저장소는 "대규모 시스템 설계 기초" 도서를 바탕으로 진행한 스터디 자료 및 예제 코드를 포함하고 있습니다.  
각 챕터별 요약 및 구현 코드는 `docs` 및 `src` 디렉토리에 정리되어 있습니다.

---

## 📚 스터디 개요

- **도서명**: 대규모 시스템 설계 기초
- **진행 방식**: 각 챕터별로 주요 내용을 정리하고, 핵심 개념을 코드로 구현
- **정리 문서 위치**: `docs/` 폴더
    - 각 챕터에 해당하는 문서를 포함

---

## ⚙️ 구현한 기능

### ✅ 안정 해시 (Consistent Hashing)

- **설명**: 서버 추가/제거 시 최소한의 데이터 이동만 발생하도록 보장하는 해싱 기법
- **패키지 위치**: `kr.study.study.ststemdesign.consistenthash`

### ✅ 고유 ID 생성기 (Snowflake)

- **설명**: 분산 환경에서 충돌 없이 고유한 ID를 생성
- **패키지 위치**: `kr.study.study.ststemdesign.uniqueid`

### ✅ URL 단축 서비스

- **설명**: 긴 URL을 짧은 문자열로 변환하고 다시 원래의 URL로 복원
- **패키지 위치**: `kr.study.study.ststemdesign.shorturl`

---

## 📁 디렉토리 구조

```bash
📦 study-system-design/
├── docs/                          # 챕터별 정리 문서
│   ├── 01_~.md
│   ├── 02_~.md
│   └── ...
├── src/
│   └── kr/
│       └── study/
│           └── study/
│               └── ststemdesign/
│                   ├── consistenthash/   # 안정 해시 구현
│                   ├── uniqueid/         # 고유 ID 생성
│                   └── shorturl/         # URL 단축 서비스
└── README.md
