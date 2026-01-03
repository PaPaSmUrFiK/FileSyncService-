package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type syncStateRepo struct {
	db *sqlx.DB
}

func NewSyncStateRepository(db *sqlx.DB) *syncStateRepo {
	return &syncStateRepo{db: db}
}

func (r *syncStateRepo) GetByDeviceID(ctx context.Context, deviceID uuid.UUID) (*domain.SyncState, error) {
	const op = "repository.postgres.GetByDeviceID"

	var state domain.SyncState
	err := r.db.GetContext(ctx, &state, "SELECT * FROM sync_state WHERE device_id = $1", deviceID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении состояния синхронизации: %w", op, err)
	}
	return &state, nil
}

func (r *syncStateRepo) Upsert(ctx context.Context, state *domain.SyncState) error {
	const op = "repository.postgres.Upsert"

	query := `
		INSERT INTO sync_state (device_id, user_id, sync_cursor, last_sync_timestamp, files_synced, pending_changes)
		VALUES (:device_id, :user_id, :sync_cursor, :last_sync_timestamp, :files_synced, :pending_changes)
		ON CONFLICT (device_id) DO UPDATE SET
			sync_cursor = EXCLUDED.sync_cursor,
			last_sync_timestamp = EXCLUDED.last_sync_timestamp,
			files_synced = EXCLUDED.files_synced,
			pending_changes = EXCLUDED.pending_changes,
			updated_at = CURRENT_TIMESTAMP
	`
	_, err := r.db.NamedExecContext(ctx, query, state)
	if err != nil {
		return fmt.Errorf("%s: ошибка при апсерте состояния синхронизации: %w", op, err)
	}
	return nil
}
