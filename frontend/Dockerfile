# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Runtime stage: static files + reverse proxy to the API -----------------
FROM nginx:stable-alpine

# Which proxy may report the client's address and scheme. The default trusts nothing but the container itself.
ENV TRUSTED_PROXY_CIDR=127.0.0.1/32

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
