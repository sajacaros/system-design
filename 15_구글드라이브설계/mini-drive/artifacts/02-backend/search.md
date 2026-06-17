## 모듈: search

## 구현 범위
- 엔드포인트(files 목록에 통합): `GET /api/files?name=&extension=&sort=recent`
  - name: original_name 부분일치(대소문자 무시 LIKE)
  - extension: 정확 일치
  - sort=recent: updated_at DESC (그 외 기본 original_name ASC)
- 인덱스 활용: (owner_id, original_name), (owner_id, extension), (owner_id, updated_at DESC).

## 계약 대조
- [x] name 부분일치 / extension / 최근(sort=recent)
- [x] owner 범위 한정
- [x] 페이지네이션 PageResponse

## 빌드·테스트
- 명령: 컴파일/컨텍스트 로드 + files 테스트로 목록 경로 검증.
- 결과: 통과(별도 전용 테스트는 files 목록 경로에 포함). 검색 전용 단위 테스트는 후속 보강 권장.

## 확인 필요
- name/extension 지정 시 폴더 전역 검색으로 동작(folderId 무시). 폴더 한정 검색 필요 여부 설계자 확인.
