package service

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository"
	"github.com/google/uuid"
)

type SyncService struct {
	deviceRepo    repository.DeviceRepository
	syncStateRepo repository.SyncStateRepository
	changelogRepo repository.ChangeLogRepository
	conflictRepo  repository.ConflictRepository
	producer      *kafka.Producer
	logger        *slog.Logger
}

func NewSyncService(
	deviceRepo repository.DeviceRepository,
	syncStateRepo repository.SyncStateRepository,
	changelogRepo repository.ChangeLogRepository,
	conflictRepo repository.ConflictRepository,
	producer *kafka.Producer,
	logger *slog.Logger,
) *SyncService {
	return &SyncService{
		deviceRepo:    deviceRepo,
		syncStateRepo: syncStateRepo,
		changelogRepo: changelogRepo,
		conflictRepo:  conflictRepo,
		producer:      producer,
		logger:        logger,
	}
}

func (s *SyncService) PushChanges(ctx context.Context, deviceID uuid.UUID, changes []domain.ChangeLog) error {
	const op = "service.sync.PushChanges"

	device, err := s.deviceRepo.GetByID(ctx, deviceID)
	if err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}
	if device == nil {
		return fmt.Errorf("%s: устройство не найдено", op)
	}

	for _, change := range changes {
		change.DeviceID = deviceID
		change.UserID = device.UserID
		if err := s.changelogRepo.Save(ctx, &change); err != nil {
			s.logger.Error("ошибка сохранения изменения", slog.String("file_id", change.FileID.String()), slog.Any("error", err))
			continue
		}
	}

	// Publish sync event
	s.producer.PublishSyncEvent(ctx, kafka.SyncEvent{
		EventType:    "sync.completed",
		SyncID:       uuid.New().String(),
		UserID:       device.UserID.String(),
		DeviceID:     deviceID.String(),
		ChangesCount: len(changes),
		Timestamp:    time.Now(),
	})

	return nil
}

func (s *SyncService) PullChanges(ctx context.Context, deviceID uuid.UUID) ([]domain.ChangeLog, error) {
	const op = "service.sync.PullChanges"

	state, err := s.syncStateRepo.GetByDeviceID(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	var since time.Time
	if state != nil && state.LastSyncTimestamp != nil {
		since = *state.LastSyncTimestamp
	}

	device, err := s.deviceRepo.GetByID(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	changes, err := s.changelogRepo.GetByUser(ctx, device.UserID, since)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	// Filter out changes from the same device
	result := make([]domain.ChangeLog, 0)
	for _, c := range changes {
		if c.DeviceID != deviceID {
			result = append(result, c)
		}
	}

	return result, nil
}
