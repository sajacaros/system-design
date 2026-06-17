## 모듈: folders

## 구현 범위
- 엔드포인트:
  - `GET /api/folders?parentId` → [{id,parentId,name,createdAt}]
  - `POST /api/folders`(parentId|null, name) → 201 {id,parentId,name}
  - `PATCH /api/folders/{id}`(name?, parentId?) → 이름변경/이동
  - `DELETE /api/folders/{id}` → 휴지통 이동(하위 폴더·파일 연쇄 DELETED)
- 엔티티/테이블: `folder`(owner_id, parent_id, name).

## 계약 대조
- [x] 요청/응답 필드 일치
- [x] 인가: owner_id==요청자, 부모 비소유 403, 미인증 401
- [x] 409 NAME_CONFLICT(동일 부모 내 동명), 400 CYCLIC_MOVE(자기/하위로 이동 금지)
- [x] 삭제 연쇄: 하위 폴더 순회 후 해당 폴더 트리의 파일 status=DELETED+deleted_at, 폴더는 구조 삭제

## 빌드·테스트
- 명령: `./gradlew test --tests '*FolderFlowTest'`
- 결과: 통과 2 / 실패 0 (생성+409중복+403타인삭제, 401 미인증).

## 확인 필요
- 폴더는 휴지통 복구 대상이 아님(휴지통은 파일 단위). 폴더 삭제 시 폴더 행 자체는 제거하고 소속 파일만 DELETED로 보존 → 폴더 복구 요구가 있으면 계약 보강 필요.
