FROM golang:1.25.3-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git

WORKDIR /app

# Copy go.mod and go.sum
COPY SyncService/go.mod SyncService/go.sum ./SyncService/
COPY filesync-internal-contracts/go.mod filesync-internal-contracts/go.sum ./filesync-internal-contracts/

WORKDIR /app/SyncService
RUN go mod download

WORKDIR /app
COPY SyncService/ ./SyncService/
COPY filesync-internal-contracts/ ./filesync-internal-contracts/

WORKDIR /app/SyncService
RUN go build -o /app/sync-service cmd/sync/main.go
RUN go build -o /app/migrator cmd/migrator/main.go

FROM alpine:latest
WORKDIR /app

# Install tools for health check
RUN apk add --no-cache netcat-openbsd

COPY --from=builder /app/sync-service .
COPY --from=builder /app/migrator .
COPY SyncService/config/config.yaml ./config/
COPY SyncService/migrations ./migrations

EXPOSE 9095 8084

# Health check: проверяем доступность gRPC порта (9095) и HTTP порта (8084)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 9095 && nc -z localhost 8084 || exit 1

# Default command starts the service
CMD ["./sync-service"]
