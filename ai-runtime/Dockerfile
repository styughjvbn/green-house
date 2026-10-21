FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

FROM node:24-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV AI_HOST=0.0.0.0
ENV AI_PORT=8091
ENV AI_CODEX_HOME=/var/lib/greenhouse-codex
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/greenhouse-codex \
    && chown node:node /var/lib/greenhouse-codex
COPY --from=build --chown=node:node /app/package.json ./
COPY --from=build --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/dist ./dist
COPY --chown=node:node contracts ./contracts
USER node
EXPOSE 8091
CMD ["node", "dist/server.js"]
