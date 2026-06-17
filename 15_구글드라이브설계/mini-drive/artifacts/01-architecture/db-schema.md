# db-schema — Mini Drive 데이터 모델 (계약 v1)

PostgreSQL. 모든 테이블 `id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `created_at`/`updated_at`은 `timestamptz NOT NULL DEFAULT now()`. 시간 컬럼은 UTC.

## 파일 상태머신

```
PENDING → UPLOADING → UPLOADED → DELETED
                 └→ FAILED (재시도 시 UPLOADING으로 복귀)
```
- 업로드 presign/등록 시 PENDING → 전송 시작 UPLOADING → 완료 UPLOADED.
- 전송 실패 시 FAILED(재시도 가능). UPLOADED만 다운로드/공유/버전 대상.
- 휴지통 이동 시 DELETED + `deleted_at` 기록(영구삭제 전까지 object 보존).

## user

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| email | varchar(255) | UNIQUE, NOT NULL |
| password | varchar(255) | NOT NULL (BCrypt 해시) |
| nickname | varchar(100) | NOT NULL |
| created_at / updated_at | timestamptz | NOT NULL |

## refresh_token  *(PRD 보강: Refresh 토큰 서버 저장·회전·로그아웃 무효화용)*

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| user_id | bigint | FK→user.id, NOT NULL |
| token_hash | varchar(255) | NOT NULL (원문 미저장, 해시) |
| expires_at | timestamptz | NOT NULL |
| revoked | boolean | NOT NULL DEFAULT false |

인덱스: `(user_id)`, `(token_hash)`.

## folder

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| parent_id | bigint | FK→folder.id, NULL=루트 |
| owner_id | bigint | FK→user.id, NOT NULL |
| name | varchar(255) | NOT NULL |

인덱스: `(owner_id, parent_id)`. 동일 부모 내 동일 이름 충돌은 서비스 계층에서 검증. 삭제는 하위 폴더/파일 휴지통 이동(연쇄).

## file

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| folder_id | bigint | FK→folder.id, NULL=루트 |
| owner_id | bigint | FK→user.id, NOT NULL |
| object_key | varchar(512) | NOT NULL (현재 버전 MinIO 키) |
| original_name | varchar(255) | NOT NULL |
| extension | varchar(50) | NULL 가능 |
| file_size | bigint | NOT NULL DEFAULT 0 |
| version | int | NOT NULL DEFAULT 1 (현재 버전 번호) |
| status | varchar(20) | NOT NULL (상태머신) |
| deleted_at | timestamptz | NULL (휴지통 이동 시각) |

인덱스: `(owner_id, folder_id)`, `(owner_id, original_name)`, `(owner_id, extension)`, `(owner_id, status)`, `(owner_id, updated_at DESC)`(최근 파일).

## file_version

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| file_id | bigint | FK→file.id, NOT NULL |
| object_key | varchar(512) | NOT NULL |
| version | int | NOT NULL |
| file_size | bigint | NOT NULL |

제약: `UNIQUE(file_id, version)`. 인덱스: `(file_id, version DESC)`. 새 버전 업로드 시 행 추가, file.version·object_key 갱신. 복구 시 이전 버전을 새 버전으로 복사 생성(이력 보존, 유실 0%).

## share_link

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| file_id | bigint | FK→file.id, NOT NULL |
| token | varchar(64) | UNIQUE, NOT NULL (URL-safe 랜덤) |
| expired_at | timestamptz | NULL=무기한 |
| is_active | boolean | NOT NULL DEFAULT true  *(PRD "링크 비활성화" 보강)* |

인덱스: `(token)`, `(file_id)`. 접근 유효 조건: `is_active=true AND (expired_at IS NULL OR expired_at > now())`. 읽기 전용(다운로드만).

## notification

| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| user_id | bigint | FK→user.id, NOT NULL |
| type | varchar(40) | NOT NULL (FILE_UPLOADED 등) |
| message | varchar(500) | NOT NULL |
| is_read | boolean | NOT NULL DEFAULT false |

인덱스: `(user_id, is_read)`, `(user_id, created_at DESC)`.

## 계약 v1.1 정합 메모 (2026-06-16, Phase B 빌드 반영)
- `user` 는 PostgreSQL/H2 예약어 → 물리 테이블명 **`users`** 로 매핑(엔티티는 User, 동작 동일).
- 폴더 휴지통: 폴더 삭제 시 폴더 행 제거 + 소속 파일 DELETED 보존. 폴더 복구는 범위 외(파일 단위 복구만).

## 확인 필요
- 없음(PRD 8장 기준 + Refresh 토큰 저장/공유 비활성화 보강 + v1.1 정합).
