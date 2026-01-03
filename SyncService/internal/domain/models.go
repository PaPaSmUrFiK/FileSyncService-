package domain

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
)

type Device struct {
	ID           uuid.UUID  `db:"id" json:"id"`
	UserID       uuid.UUID  `db:"user_id" json:"user_id"`
	DeviceName   string     `db:"device_name" json:"device_name"`
	DeviceType   string     `db:"device_type" json:"device_type"`
	OS           string     `db:"os" json:"os"`
	OSVersion    string     `db:"os_version" json:"os_version"`
	SyncToken    string     `db:"sync_token" json:"sync_token"`
	LastSyncAt   *time.Time `db:"last_sync_at" json:"last_sync_at"`
	IsActive     bool       `db:"is_active" json:"is_active"`
	RegisteredAt time.Time  `db:"registered_at" json:"registered_at"`
}

type SyncState struct {
	ID                uuid.UUID  `db:"id" json:"id"`
	DeviceID          uuid.UUID  `db:"device_id" json:"device_id"`
	UserID            uuid.UUID  `db:"user_id" json:"user_id"`
	SyncCursor        string     `db:"sync_cursor" json:"sync_cursor"`
	LastSyncTimestamp *time.Time `db:"last_sync_timestamp" json:"last_sync_timestamp"`
	FilesSynced       int        `db:"files_synced" json:"files_synced"`
	PendingChanges    int        `db:"pending_changes" json:"pending_changes"`
	UpdatedAt         time.Time  `db:"updated_at" json:"updated_at"`
}

type ChangeLog struct {
	ID         uuid.UUID       `db:"id" json:"id"`
	ChangeID   string          `db:"change_id" json:"change_id"`
	FileID     uuid.UUID       `db:"file_id" json:"file_id"`
	UserID     uuid.UUID       `db:"user_id" json:"user_id"`
	DeviceID   uuid.UUID       `db:"device_id" json:"device_id"`
	ChangeType string          `db:"change_type" json:"change_type"`
	FilePath   string          `db:"file_path" json:"file_path"`
	FileHash   string          `db:"file_hash" json:"file_hash"`
	FileSize   int64           `db:"file_size" json:"file_size"`
	Version    int32           `db:"version" json:"version"`
	Timestamp  time.Time       `db:"timestamp" json:"timestamp"`
	Metadata   json.RawMessage `db:"metadata" json:"metadata"`
}

type SyncConflict struct {
	ID             uuid.UUID  `db:"id" json:"id"`
	FileID         uuid.UUID  `db:"file_id" json:"file_id"`
	UserID         uuid.UUID  `db:"user_id" json:"user_id"`
	ConflictType   string     `db:"conflict_type" json:"conflict_type"`
	DeviceAID      uuid.UUID  `db:"device_a_id" json:"device_a_id"`
	DeviceBID      uuid.UUID  `db:"device_b_id" json:"device_b_id"`
	VersionA       int32      `db:"version_a" json:"version_a"`
	VersionB       int32      `db:"version_b" json:"version_b"`
	Resolved       bool       `db:"resolved" json:"resolved"`
	ResolutionType string     `db:"resolution_type" json:"resolution_type"`
	ConflictFileID *uuid.UUID `db:"conflict_file_id" json:"conflict_file_id"`
	CreatedAt      time.Time  `db:"created_at" json:"created_at"`
	ResolvedAt     *time.Time `db:"resolved_at" json:"resolved_at"`
}
