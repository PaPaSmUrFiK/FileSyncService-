package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/domain"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type storageRepo struct {
	db *sqlx.DB
}

func NewStorageRepository(db *sqlx.DB) *storageRepo {
	return &storageRepo{db: db}
}

func (r *storageRepo) SaveMapping(ctx context.Context, mapping *domain.StorageMapping) error {
	const op = "repository.postgres.SaveMapping"

	query := `
		INSERT INTO storage_mappings (file_id, storage_path, bucket, size, hash)
		VALUES (:file_id, :storage_path, :bucket, :size, :hash)
		ON CONFLICT (file_id) DO UPDATE SET
			storage_path = EXCLUDED.storage_path,
			bucket = EXCLUDED.bucket,
			size = EXCLUDED.size,
			hash = EXCLUDED.hash,
			stored_at = CURRENT_TIMESTAMP
	`
	_, err := r.db.NamedExecContext(ctx, query, mapping)
	if err != nil {
		return fmt.Errorf("%s: ошибка при сохранении маппинга: %w", op, err)
	}
	return nil
}

func (r *storageRepo) GetMappingByFileID(ctx context.Context, fileID uuid.UUID) (*domain.StorageMapping, error) {
	const op = "repository.postgres.GetMappingByFileID"

	var mapping domain.StorageMapping
	err := r.db.GetContext(ctx, &mapping, "SELECT * FROM storage_mappings WHERE file_id = $1", fileID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении маппинга: %w", op, err)
	}
	return &mapping, nil
}

func (r *storageRepo) DeleteMapping(ctx context.Context, fileID uuid.UUID) error {
	const op = "repository.postgres.DeleteMapping"

	_, err := r.db.ExecContext(ctx, "DELETE FROM storage_mappings WHERE file_id = $1", fileID)
	if err != nil {
		return fmt.Errorf("%s: ошибка при удалении маппинга: %w", op, err)
	}
	return nil
}

func (r *storageRepo) SaveVersion(ctx context.Context, version *domain.StorageVersion) error {
	const op = "repository.postgres.SaveVersion"

	query := `
		INSERT INTO storage_versions (file_id, version, storage_path, bucket, size)
		VALUES (:file_id, :version, :storage_path, :bucket, :size)
		ON CONFLICT (file_id, version) DO NOTHING
	`
	_, err := r.db.NamedExecContext(ctx, query, version)
	if err != nil {
		return fmt.Errorf("%s: ошибка при сохранении версии: %w", op, err)
	}
	return nil
}

func (r *storageRepo) GetVersionsByFileID(ctx context.Context, fileID uuid.UUID) ([]domain.StorageVersion, error) {
	const op = "repository.postgres.GetVersionsByFileID"

	var versions []domain.StorageVersion
	err := r.db.SelectContext(ctx, &versions, "SELECT * FROM storage_versions WHERE file_id = $1 ORDER BY version DESC", fileID)
	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при получении списка версий: %w", op, err)
	}
	return versions, nil
}

func (r *storageRepo) GetVersion(ctx context.Context, fileID uuid.UUID, version int32) (*domain.StorageVersion, error) {
	const op = "repository.postgres.GetVersion"

	var v domain.StorageVersion
	err := r.db.GetContext(ctx, &v, "SELECT * FROM storage_versions WHERE file_id = $1 AND version = $2", fileID, version)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении конкретной версии: %w", op, err)
	}
	return &v, nil
}

func (r *storageRepo) DeleteVersion(ctx context.Context, fileID uuid.UUID, version int32) error {
	const op = "repository.postgres.DeleteVersion"

	_, err := r.db.ExecContext(ctx, "DELETE FROM storage_versions WHERE file_id = $1 AND version = $2", fileID, version)
	if err != nil {
		return fmt.Errorf("%s: ошибка при удалении версии: %w", op, err)
	}
	return nil
}

func (r *storageRepo) DeleteAllVersions(ctx context.Context, fileID uuid.UUID) error {
	const op = "repository.postgres.DeleteAllVersions"

	_, err := r.db.ExecContext(ctx, "DELETE FROM storage_versions WHERE file_id = $1", fileID)
	if err != nil {
		return fmt.Errorf("%s: ошибка при удалении всех версий: %w", op, err)
	}
	return nil
}
