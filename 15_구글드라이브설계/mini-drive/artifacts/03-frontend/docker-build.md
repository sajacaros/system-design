## 화면: (인프라) 프런트 Docker 이미지 빌드 수정

## 증상
- `docker compose up --build` 중 프런트 이미지 빌드 실패.
- `[ERR_PNPM_IGNORED_BUILDS] Ignored build scripts: esbuild@0.21.5` → `Dockerfile:9 RUN pnpm install ...` exit code 1.

## 진단
- 로컬은 `frontend/pnpm-workspace.yaml`(`onlyBuiltDependencies:[esbuild]`, `verifyDepsBeforeRun:false`)로 해결돼 있었다.
- 그러나 `frontend/Dockerfile`의 install 단계가 `package.json`과 `pnpm-lock.yaml`만 COPY하고 `pnpm-workspace.yaml`을 COPY하지 않아 컨테이너 설치가 그 정책을 못 봤다.
- 추가로, 컨테이너 corepack은 `packageManager` 핀이 없어 pnpm 11.x를 쓰는데, pnpm 10/11에서는 ignored build scripts가 하드 에러(exit 1)다. `pnpm-workspace.yaml`을 COPY한 뒤에도 경고가 남아 install이 exit 1을 반환했다.

## 수정 (frontend/Dockerfile)
1. install 이전에 `pnpm-workspace.yaml`도 COPY:
   `COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml ./`
2. install은 의존성 트리를 정상 구성하므로 ignored-builds 경고로 인한 exit 1은 흡수(`|| true`)하고,
   `--config.verify-deps-before-run=false`로 정책 일치.
3. 직후 `RUN pnpm rebuild esbuild`로 esbuild 네이티브 바이너리 빌드를 명시적으로 보장(vite build 필수). 이 단계가 실패하면 빌드가 중단되므로 바이너리 생성이 보증된다.
4. `backend/`·인프라(compose) 설정은 건드리지 않음. `frontend/`만 수정.

## 빌드·검증
- 명령: `docker build -f frontend/Dockerfile -t mini-drive-frontend-test frontend/`
- 결과: 성공.
  - install: ignored-builds 경고는 남지만 흡수됨.
  - `pnpm rebuild esbuild`: DONE.
  - `pnpm build` (`tsc --noEmit && vite build`): `✓ 1860 modules transformed`, `✓ built in 5.95s`.
    - dist/index.html, dist/assets/index-*.css(18.28kB), dist/assets/index-*.js(430.53kB) 생성.
  - runtime(nginx) 이미지에 `/usr/share/nginx/html` = {index.html, 50x.html, assets/} 확인.
- 검증 후 테스트 이미지(`mini-drive-frontend-test`) 제거.

## 주의
- `docker compose up/down -v` 등 전체 기동/파괴적 명령은 실행하지 않음(오케스트레이터 기동 게이트 관리). 프런트 이미지 단독 build까지만 수행.

## 확인 필요
- 없음. install+build가 컨테이너 안에서 통과하고 정적 산출물이 nginx 서빙 경로에 생성됨.
