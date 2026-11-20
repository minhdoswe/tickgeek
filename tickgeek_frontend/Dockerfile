FROM node:26-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN mkdir -p public && npm run build

FROM node:26-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

FROM node:26-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules node_modules
COPY --from=build /app/package.json /app/next.config.ts ./
COPY --from=build /app/public public
COPY --from=build /app/.next .next
USER node
EXPOSE 3000
# API_URL and CLIENT_IP_HEADER are read at runtime, so set them on the container.
CMD ["node_modules/.bin/next", "start", "-H", "0.0.0.0", "-p", "3000"]
