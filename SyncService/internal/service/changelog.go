package service

import (
	"context"
	"fmt"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository"
	"github.com/google/uuid"
)

type ChangelogService struct {
	changelogRepo repository.ChangeLogRepository
}

func NewChangelogService(repo repository.ChangeLogRepository) *ChangelogService {
	return &ChangelogService{changelogRepo: repo}
}

func (s *ChangelogService) LogChange(ctx context.Context, change *domain.ChangeLog) error {
	const op = "service.changelog.LogChange"

	if change.ChangeID == "" {
		change.ChangeID = uuid.New().String()
	}
	if change.Timestamp.IsZero() {
		change.Timestamp = time.Now()
	}

	if err := s.changelogRepo.Save(ctx, change); err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}

	return nil
}

func (s *ChangelogService) GetChangesSince(ctx context.Context, userID uuid.UUID, since time.Time) ([]domain.ChangeLog, error) {
	const op = "service.changelog.GetChangesSince"

	changes, err := s.changelogRepo.GetByUser(ctx, userID, since)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	return changes, nil
}
