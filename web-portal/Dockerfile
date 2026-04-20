# ═══════════════════════════════════════════════════════════════════
# RoadGuard Web Portal — Multi-stage Dockerfile
#
# Stage 1 (builder): npm ci + vite build -> static assets in /app/dist-app
# Stage 2 (runtime): nginx:alpine serves the static SPA
#
# Result: ~25MB final image (vs ~1GB with a full Node image)
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Node builder ────────────────────────────────────────────
FROM node:24-alpine AS builder

WORKDIR /app

# Copy lock files first (maximises Docker layer caching)
COPY package.json package-lock.json ./

# Install exact deps from lockfile (reproducible, no devDeps in runtime)
RUN npm ci

# Copy source and build
COPY . .

RUN npm run build


# ── Stage 2: Rootless Nginx runtime ────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

LABEL org.opencontainers.image.title="RoadGuard Web Portal"
LABEL org.opencontainers.image.description="React SPA served by Nginx for the RoadGuard operator dashboard"
LABEL org.opencontainers.image.version="1.0.0"

# Copy built assets from builder
COPY --from=builder /app/dist-app /usr/share/nginx/html

# Custom Nginx config for SPA routing + security headers + proxy to analytics
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Apply latest package patches in runtime image.
USER root
RUN apk upgrade --no-cache
USER 101

# Remove default nginx config
RUN rm -f /etc/nginx/conf.d/default.conf.bak

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/health.txt || exit 1

CMD ["nginx", "-g", "daemon off;"]
