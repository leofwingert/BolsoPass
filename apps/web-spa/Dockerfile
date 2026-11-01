FROM node:22-alpine AS build

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web-spa/package.json apps/web-spa/package.json
RUN corepack enable && pnpm install --frozen-lockfile

COPY apps/web-spa apps/web-spa
ENV VITE_API_MODE=http
ENV VITE_API_BASE_URL=/api/v1
RUN pnpm --filter web-spa build

FROM nginx:1.27-alpine
COPY apps/web-spa/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/web-spa/dist /usr/share/nginx/html

EXPOSE 3000
