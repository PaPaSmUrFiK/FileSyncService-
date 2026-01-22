FROM golang:1.25.3-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git

WORKDIR /app

# Copy go.mod and go.sum
COPY SyncService/go.mod SyncService/go.sum ./SyncService/

WORKDIR /app/SyncService
RUN go mod download

WORKDIR /app
COPY SyncService/ ./SyncService/

WORKDIR /app/SyncService
RUN go build -o /app/sync-service cmd/sync/main.go

FROM alpine:latest
WORKDIR /app

# Install tools for health check
RUN apk add --no-cache netcat-openbsd

COPY --from=builder /app/sync-service .
COPY SyncService/config/config.yaml ./config/

EXPOSE 8084

# Health check: проверяем доступность HTTP порта (8084)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 8084 || exit 1

# Default command starts the service
CMD ["./sync-service"]
