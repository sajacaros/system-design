## 화면: trash
휴지통 파일 목록, 복구, 영구 삭제.

## 사용 엔드포인트/이벤트
- GET /api/files?status=DELETED&page=&size= → Page<FileListItem>
- POST /api/files/{id}/restore (body 없음) → 휴지통 복원(DELETED→UPLOADED)
- DELETE /api/files/{id}/permanent → 204 (DELETED만 허용, 409면 휴지통 아님)

## 계약 대조
- [x] 응답 타입 일치 — status=DELETED 목록도 FileListItem 동일 형태.
- [x] 휴지통 복원은 body 없이 POST /restore (특정 버전 복구와 구분).
- [x] 영구삭제는 DELETE /permanent.
- [x] 인증/토큰 갱신 처리, 동작 후 files 캐시 무효화.

## 빌드·타입체크
- 명령: `pnpm build`
- 결과: 통과

## 확인 필요
- 휴지통 화면은 파일만 표시. 폴더 휴지통 조회/복구는 계약에 폴더용 status 조회·복구 엔드포인트가 없음(폴더 DELETE는 연쇄 휴지통 이동, 복구 API 미정의) → 폴더 복구 UI는 보류, 확인 필요.
