FROM node:20-alpine AS build

WORKDIR /app

# Accept build argument for API URL
ARG VITE_API_URL=/api/v1
# EH-4.4: optional Sentry DSN baked into the bundle at build time (VITE_ vars are build-time).
# Empty by default → Sentry stays dormant (the SDK is never even loaded).
ARG VITE_SENTRY_DSN=
ARG VITE_SENTRY_ENVIRONMENT=production

COPY package*.json ./
RUN npm install

COPY . .

# Set environment variables for build
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN
ENV VITE_SENTRY_ENVIRONMENT=$VITE_SENTRY_ENVIRONMENT

RUN npm run build

FROM nginx:alpine

# Set timezone to Tashkent (GMT+5)
ENV TZ=Asia/Tashkent
RUN apk add --no-cache tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
