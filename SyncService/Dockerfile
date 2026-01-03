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
COPY --from=builder /app/sync-service .
COPY --from=builder /app/migrator .
COPY SyncService/config/config.yaml ./config/
COPY SyncService/migrations ./migrations

EXPOSE 9095 8084

# Default command starts the service
CMD ["./sync-service"]
