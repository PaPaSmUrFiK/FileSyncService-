package repository

import (
	"context"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/google/uuid"
)

type DeviceRepository interface {
	Register(ctx context.Context, device *domain.Device) error
	GetByID(ctx context.Context, id uuid.UUID) (*domain.Device, error)
	GetByToken(ctx context.Context, token string) (*domain.Device, error)
	GetByUserID(ctx context.Context, userID uuid.UUID) ([]domain.Device, error)
	Update(ctx context.Context, device *domain.Device) error
	Delete(ctx context.Context, id uuid.UUID) error
}

type SyncStateRepository interface {
	GetByDeviceID(ctx context.Context, deviceID uuid.UUID) (*domain.SyncState, error)
	Upsert(ctx context.Context, state *domain.SyncState) error
}

type ChangeLogRepository interface {
	Save(ctx context.Context, entry *domain.ChangeLog) error
	GetByUser(ctx context.Context, userID uuid.UUID, since time.Time) ([]domain.ChangeLog, error)
}

type ConflictRepository interface {
	Save(ctx context.Context, conflict *domain.SyncConflict) error
	GetByID(ctx context.Context, id uuid.UUID) (*domain.SyncConflict, error)
	GetByUserID(ctx context.Context, userID uuid.UUID) ([]domain.SyncConflict, error)
	Update(ctx context.Context, conflict *domain.SyncConflict) error
}
