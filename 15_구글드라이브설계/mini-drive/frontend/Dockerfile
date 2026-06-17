# syntax=docker/dockerfile:1
# 멀티스테이지: pnpm으로 정적 빌드 → nginx로 서빙.
# compose가 ./frontend 컨텍스트로 빌드한다.

FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable
# pnpm-workspace.yaml(onlyBuiltDependencies, verifyDepsBeforeRun)을 install 이전에 COPY해야
# 컨테이너 설치가 동일한 빌드 정책을 본다.
COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml ./
# pnpm 10/11은 ignored build scripts(예: esbuild)를 ERR_PNPM_IGNORED_BUILDS로 exit 1 처리한다.
# install 자체는 의존성 트리를 정상 구성하므로, 해당 경고는 무시하고
# 직후 esbuild 바이너리 빌드를 명시적으로 수행한다.
RUN pnpm install --frozen-lockfile --config.verify-deps-before-run=false \
    || pnpm install --frozen-lockfile --config.verify-deps-before-run=false \
    || true
# esbuild 네이티브 바이너리 보장(vite build에 필수). 실패 시 빌드 중단.
RUN pnpm rebuild esbuild
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
