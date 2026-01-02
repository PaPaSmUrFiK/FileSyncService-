package repository

import (
	"context"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/domain"
	"github.com/google/uuid"
)

type StorageRepository interface {
	SaveMapping(ctx context.Context, mapping *domain.StorageMapping) error
	GetMappingByFileID(ctx context.Context, fileID uuid.UUID) (*domain.StorageMapping, error)
	DeleteMapping(ctx context.Context, fileID uuid.UUID) error

	SaveVersion(ctx context.Context, version *domain.StorageVersion) error
	GetVersionsByFileID(ctx context.Context, fileID uuid.UUID) ([]domain.StorageVersion, error)
	GetVersion(ctx context.Context, fileID uuid.UUID, version int32) (*domain.StorageVersion, error)
	DeleteVersion(ctx context.Context, fileID uuid.UUID, version int32) error
	DeleteAllVersions(ctx context.Context, fileID uuid.UUID) error
}
