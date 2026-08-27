# ── Build stage ─────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite bakes VITE_* vars into the JS bundle at build time — pass them as
# build args (not runtime env, which the static bundle can't read).
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_RAZORPAY_KEY_ID=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_RAZORPAY_KEY_ID=${VITE_RAZORPAY_KEY_ID}

RUN npm run build

# ── Runtime stage ───────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost:80/ || exit 1
