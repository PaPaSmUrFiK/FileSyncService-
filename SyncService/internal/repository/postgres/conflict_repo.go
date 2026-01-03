package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type conflictRepo struct {
	db *sqlx.DB
}

func NewConflictRepository(db *sqlx.DB) *conflictRepo {
	return &conflictRepo{db: db}
}

func (r *conflictRepo) Save(ctx context.Context, conflict *domain.SyncConflict) error {
	const op = "repository.postgres.SaveConflict"

	query := `
		INSERT INTO sync_conflicts (file_id, user_id, conflict_type, device_a_id, device_b_id, version_a, version_b)
		VALUES (:file_id, :user_id, :conflict_type, :device_a_id, :device_b_id, :version_a, :version_b)
		RETURNING id, created_at
	`
	rows, err := r.db.NamedQueryContext(ctx, query, conflict)
	if err != nil {
		return fmt.Errorf("%s: ошибка при сохранении конфликта: %w", op, err)
	}
	defer rows.Close()

	if rows.Next() {
		if err := rows.Scan(&conflict.ID, &conflict.CreatedAt); err != nil {
			return fmt.Errorf("%s: ошибка при сканировании результата: %w", op, err)
		}
	}

	return nil
}

func (r *conflictRepo) GetByID(ctx context.Context, id uuid.UUID) (*domain.SyncConflict, error) {
	const op = "repository.postgres.GetConflictByID"

	var conflict domain.SyncConflict
	err := r.db.GetContext(ctx, &conflict, "SELECT * FROM sync_conflicts WHERE id = $1", id)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении конфликта по ID: %w", op, err)
	}
	return &conflict, nil
}

func (r *conflictRepo) GetByUserID(ctx context.Context, userID uuid.UUID) ([]domain.SyncConflict, error) {
	const op = "repository.postgres.GetConflictsByUserID"

	var conflicts []domain.SyncConflict
	err := r.db.SelectContext(ctx, &conflicts, "SELECT * FROM sync_conflicts WHERE user_id = $1", userID)
	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при получении конфликтов пользователя: %w", op, err)
	}
	return conflicts, nil
}

func (r *conflictRepo) Update(ctx context.Context, conflict *domain.SyncConflict) error {
	const op = "repository.postgres.UpdateConflict"

	query := `
		UPDATE sync_conflicts SET
			resolved = :resolved,
			resolution_type = :resolution_type,
			conflict_file_id = :conflict_file_id,
			resolved_at = :resolved_at
		WHERE id = :id
	`
	_, err := r.db.NamedExecContext(ctx, query, conflict)
	if err != nil {
		return fmt.Errorf("%s: ошибка при обновлении конфликта: %w", op, err)
	}
	return nil
}
