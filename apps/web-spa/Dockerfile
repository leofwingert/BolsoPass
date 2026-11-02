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
# openssl gera o certificado HTTPS de desenvolvimento (script roda antes do nginx subir).
RUN apk add --no-cache openssl
COPY apps/web-spa/nginx.conf /etc/nginx/conf.d/default.conf
COPY apps/web-spa/docker/locations.conf /etc/nginx/bolsopass-locations.conf
COPY apps/web-spa/docker/10-gen-cert.sh /docker-entrypoint.d/10-gen-cert.sh
# Remove CR caso o arquivo venha do Windows com fim de linha CRLF.
RUN sed -i 's/\r$//' /docker-entrypoint.d/10-gen-cert.sh && chmod +x /docker-entrypoint.d/10-gen-cert.sh
COPY --from=build /app/apps/web-spa/dist /usr/share/nginx/html

EXPOSE 3000 3443
