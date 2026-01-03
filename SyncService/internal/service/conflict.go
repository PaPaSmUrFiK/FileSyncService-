package service

import (
	"context"
	"fmt"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository"
	"github.com/google/uuid"
)

type ConflictService struct {
	conflictRepo repository.ConflictRepository
}

func NewConflictService(repo repository.ConflictRepository) *ConflictService {
	return &ConflictService{conflictRepo: repo}
}

func (s *ConflictService) RegisterConflict(ctx context.Context, conflict *domain.SyncConflict) error {
	const op = "service.conflict.RegisterConflict"

	if err := s.conflictRepo.Save(ctx, conflict); err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}

	return nil
}

func (s *ConflictService) ResolveConflict(ctx context.Context, id uuid.UUID, resType string, conflictFileID *uuid.UUID) error {
	const op = "service.conflict.ResolveConflict"

	conflict, err := s.conflictRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}
	if conflict == nil {
		return fmt.Errorf("%s: конфликт не найден", op)
	}

	conflict.Resolved = true
	conflict.ResolutionType = resType
	conflict.ConflictFileID = conflictFileID
	now := time.Now()
	conflict.ResolvedAt = &now

	if err := s.conflictRepo.Update(ctx, conflict); err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}

	return nil
}
