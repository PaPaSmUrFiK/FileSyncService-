package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type deviceRepo struct {
	db *sqlx.DB
}

func NewDeviceRepository(db *sqlx.DB) *deviceRepo {
	return &deviceRepo{db: db}
}

func (r *deviceRepo) Register(ctx context.Context, device *domain.Device) error {
	const op = "repository.postgres.Register"

	query := `
		INSERT INTO devices (user_id, device_name, device_type, os, os_version, sync_token)
		VALUES (:user_id, :device_name, :device_type, :os, :os_version, :sync_token)
		RETURNING id, registered_at
	`
	rows, err := r.db.NamedQueryContext(ctx, query, device)
	if err != nil {
		return fmt.Errorf("%s: ошибка при регистрации устройства: %w", op, err)
	}
	defer rows.Close()

	if rows.Next() {
		if err := rows.Scan(&device.ID, &device.RegisteredAt); err != nil {
			return fmt.Errorf("%s: ошибка при сканировании результата: %w", op, err)
		}
	}

	return nil
}

func (r *deviceRepo) GetByID(ctx context.Context, id uuid.UUID) (*domain.Device, error) {
	const op = "repository.postgres.GetByID"

	var device domain.Device
	err := r.db.GetContext(ctx, &device, "SELECT * FROM devices WHERE id = $1", id)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении устройства по ID: %w", op, err)
	}
	return &device, nil
}

func (r *deviceRepo) GetByToken(ctx context.Context, token string) (*domain.Device, error) {
	const op = "repository.postgres.GetByToken"

	var device domain.Device
	err := r.db.GetContext(ctx, &device, "SELECT * FROM devices WHERE sync_token = $1", token)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("%s: ошибка при получении устройства по токену: %w", op, err)
	}
	return &device, nil
}

func (r *deviceRepo) GetByUserID(ctx context.Context, userID uuid.UUID) ([]domain.Device, error) {
	const op = "repository.postgres.GetByUserID"

	var devices []domain.Device
	err := r.db.SelectContext(ctx, &devices, "SELECT * FROM devices WHERE user_id = $1", userID)
	if err != nil {
		return nil, fmt.Errorf("%s: ошибка при получении списка устройств пользователя: %w", op, err)
	}
	return devices, nil
}

func (r *deviceRepo) Update(ctx context.Context, device *domain.Device) error {
	const op = "repository.postgres.Update"

	query := `
		UPDATE devices SET
			device_name = :device_name,
			device_type = :device_type,
			os = :os,
			os_version = :os_version,
			last_sync_at = :last_sync_at,
			is_active = :is_active
		WHERE id = :id
	`
	_, err := r.db.NamedExecContext(ctx, query, device)
	if err != nil {
		return fmt.Errorf("%s: ошибка при обновлении устройства: %w", op, err)
	}
	return nil
}

func (r *deviceRepo) Delete(ctx context.Context, id uuid.UUID) error {
	const op = "repository.postgres.Delete"

	_, err := r.db.ExecContext(ctx, "DELETE FROM devices WHERE id = $1", id)
	if err != nil {
		return fmt.Errorf("%s: ошибка при удалении устройства: %w", op, err)
	}
	return nil
}
