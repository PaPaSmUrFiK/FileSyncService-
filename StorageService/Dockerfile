FROM golang:1.25.3-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git

WORKDIR /app

# Copy go.mod and go.sum
COPY StorageService/go.mod StorageService/go.sum ./StorageService/
COPY filesync-internal-contracts/go.mod filesync-internal-contracts/go.sum ./filesync-internal-contracts/

WORKDIR /app/StorageService
RUN go mod download

WORKDIR /app
COPY StorageService/ ./StorageService/
COPY filesync-internal-contracts/ ./filesync-internal-contracts/

WORKDIR /app/StorageService
RUN go build -o /app/storage-service cmd/storage/main.go
RUN go build -o /app/migrator cmd/migrator/main.go

FROM alpine:latest
WORKDIR /app

# Install tools for health check
RUN apk add --no-cache netcat-openbsd

COPY --from=builder /app/storage-service .
COPY --from=builder /app/migrator .
COPY StorageService/config/config.yaml ./config/
COPY StorageService/migrations ./migrations

EXPOSE 9094

# Health check: проверяем доступность gRPC порта
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 9094 || exit 1

# Default command starts the service
CMD ["./storage-service"]
