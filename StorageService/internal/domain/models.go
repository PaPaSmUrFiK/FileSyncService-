package domain

import (
	"time"

	"github.com/google/uuid"
)

type StorageMapping struct {
	ID          uuid.UUID `db:"id"`
	FileID      uuid.UUID `db:"file_id"`
	StoragePath string    `db:"storage_path"`
	Bucket      string    `db:"bucket"`
	Size        int64     `db:"size"`
	Hash        string    `db:"hash"`
	StoredAt    time.Time `db:"stored_at"`
}

type StorageVersion struct {
	ID          uuid.UUID `db:"id"`
	FileID      uuid.UUID `db:"file_id"`
	Version     int32     `db:"version"`
	StoragePath string    `db:"storage_path"`
	Bucket      string    `db:"bucket"`
	Size        int64     `db:"size"`
	StoredAt    time.Time `db:"stored_at"`
}
