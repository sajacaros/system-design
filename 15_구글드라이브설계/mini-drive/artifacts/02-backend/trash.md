## 모듈: trash

## 구현 범위
- 엔드포인트:
  - 휴지통 조회: `GET /api/files?status=DELETED` (전역 휴지통 = 폴더 무관 전체, A6 반영)
  - 휴지통 이동: `DELETE /api/files/{id}` (status=DELETED, deleted_at 기록; object 보존)
  - 복구: `POST /api/files/{id}/restore` (body 없음) → DELETED→UPLOADED, deleted_at=null
  - 영구삭제: `DELETE /api/files/{id}/permanent` → DELETED 상태만 허용, MinIO object(현재+versions/*+thumbnail) + file_version 정리
- 이벤트: FILE_DELETED(휴지통 이동), FILE_UPDATED(복구).

## 계약 대조
- [x] 휴지통 이동 시 object 보존(영구삭제 전까지)
- [x] 영구삭제는 DELETED 상태만, 아니면 409 CONFLICT
- [x] 인가 owner 검증
- [x] 전역 휴지통 조회(A6): folderId 미지정 시 DELETED 전체 반환
- [x] folderId="null"/미지정 안전 처리(A7): 500/내부 메시지 누출 제거

## E2E 결함 수정 (2026-06-16, e2e-conformance.md A6/A7)

### A7 (버그/우선) — folderId="null" 리터럴 500 누출 제거
- 원인: `FileController.list`의 `folderId` 파라미터 타입이 `Long`이라, 문자열 `"null"`을
  Spring이 Long으로 변환하다 `MethodArgumentTypeMismatchException` 발생 → 전역 catch-all이
  500 INTERNAL_ERROR + `For input string: "null"` 내부 메시지 누출.
- 수정: `folderId`를 `String`(nullable)으로 받아 서비스 호출 전 안전 파싱.
  - 미지정/공백/`"null"`(대소문자 무관) → 루트(null)
  - 숫자 → 해당 폴더 id
  - 그 외 비숫자 → `ApiException(VALIDATION)` → 400 VALIDATION (500 아님, 내부 메시지 미누출)
- 파일: `backend/src/main/java/com/minidrive/file/FileController.java`

### A6 — 전역 휴지통 조회 정의
- 원인: `FileService.list`가 `status=DELETED`여도 folderId 미지정 시 항상 `folderId IS NULL`
  술어를 추가 → 루트 외 폴더의 DELETED 파일이 휴지통 화면에서 누락(빈 목록).
- 수정: `globalTrash = (status==DELETED && folderId==null)`이면 folderId 술어를 생략하여
  소유자의 모든 폴더 DELETED 파일 전체 반환. 숫자 folderId 지정 시 기존대로 폴더 한정.
  (owner_id 술어는 항상 유지 → 인가 경계 불변)
- 파일: `backend/src/main/java/com/minidrive/file/FileService.java`
- 계약 보강: `artifacts/01-architecture/api-contract.md` GET /api/files 항목에 v1.3 메모 2줄 추가.

## 빌드·테스트
- 명령: `./gradlew build`
- 결과: BUILD SUCCESSFUL. 전체 테스트 통과 16 / 실패 0 (VersionTrashFlowTest 2→4건 증설).
- 추가 테스트 (VersionTrashFlowTest):
  - `trash_globalView_spansAllFolders` (A6): 루트+폴더 내 파일 삭제 후 folderId 미지정 → totalElements 2,
    folderId 지정 → 1건만.
  - `list_folderIdLiteralNull_isHandledSafely` (A7): `folderId=null&status=DELETED` → 200,
    `folderId=null`(UPLOADED) → 루트 1건, `folderId=abc` → 400 VALIDATION (500 아님).

## 확인 필요
- restore 분기: body에 {version} 있으면 버전 복구, 없으면 휴지통 복구(계약 그대로). 휴지통 파일에 대해 {version}을 보내면 버전 복구가 우선됨 — 계약상 둘을 동시 충족하는 경우 우선순위는 미명시(현재 version 우선).
- A6 의미 정의(전역 휴지통): folderId 리터럴 `"null"`도 미지정과 동일하게 전역 휴지통으로 처리. 휴지통은 폴더 무관 전역 보기가 자연스럽다는 판단(설계자 정합용 계약 메모 v1.3 반영).
