package postgres

import (
	"context"
	"fmt"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type changeLogRepo struct {
	db *sqlx.DB
}

func NewChangeLogRepository(db *sqlx.DB) *changeLogRepo {
	return &changeLogRepo{db: db}
}

func (r *changeLogRepo) Save(ctx context.Context, entry *domain.ChangeLog) error {
	const op = "repository.postgres.SaveChangeLog"

	query := `
		INSERT INTO change_log (change_id, file_id, user_id, device_id, change_type, file_path, file_hash, file_size, version, timestamp, metadata)
		VALUES (:change_id, :file_id, :user_id, :device_id, :change_type, :file_path, :file_hash, :file_size, :version, :timestamp, :metadata)
	`
	_, err := r.db.NamedExecContext(ctx, query, entry)
	if err != nil {
		return fmt.Errorf("%s: ошибка при сохранении лога изменений: %w", op, err)
	}
	return nil
}

func (r *changeLogRepo) GetByUser(ctx context.Context, userID uuid.UUID, since time.Time) ([]domain.ChangeLog, error) {
	const op = "repository.postgres.GetChangeLogByUser"

	var logs []domain.ChangeLog
	err := r.db.SelectContext(ctx, &logs, "SELECT * FROM change_log WHERE user_id = $1 AND timestamp > $2 ORDER BY timestamp ASC", userID, since)
	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при получении логов изменений: %w", op, err)
	}
	return logs, nil
}
