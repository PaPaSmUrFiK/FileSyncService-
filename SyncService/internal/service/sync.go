package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository"
	"github.com/google/uuid"
	kafkalib "github.com/segmentio/kafka-go"
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

// HandleFileEvent обрабатывает события файлов из Kafka
func (s *SyncService) HandleFileEvent(ctx context.Context, msg kafkalib.Message) error {
	const op = "service.sync.HandleFileEvent"

	var fileEvent kafka.FileEvent
	if err := json.Unmarshal(msg.Value, &fileEvent); err != nil {
		// Попробуем парсить как Map для совместимости с Java сервисами
		var eventMap map[string]interface{}
		if err2 := json.Unmarshal(msg.Value, &eventMap); err2 != nil {
			s.logger.Error("ошибка парсинга file event", slog.Any("error", err), slog.Any("error2", err2))
			return fmt.Errorf("%s: %w", op, err2)
		}

		// Извлекаем поля из Map
		fileEvent.EventType = getString(eventMap, "event_type", "eventType")
		fileEvent.FileID = getString(eventMap, "file_id", "fileId")
		fileEvent.UserID = getString(eventMap, "user_id", "userId")
		if name, ok := eventMap["file_name"].(string); ok {
			fileEvent.FileName = name
		} else if name, ok := eventMap["fileName"].(string); ok {
			fileEvent.FileName = name
		}
	}

	// Создаем ChangeLog для синхронизации
	fileID, err := uuid.Parse(fileEvent.FileID)
	if err != nil {
		s.logger.Error("неверный file_id в событии", slog.String("file_id", fileEvent.FileID))
		return fmt.Errorf("%s: invalid file_id: %w", op, err)
	}

	userID, err := uuid.Parse(fileEvent.UserID)
	if err != nil {
		s.logger.Error("неверный user_id в событии", slog.String("user_id", fileEvent.UserID))
		return fmt.Errorf("%s: invalid user_id: %w", op, err)
	}

	// Определяем тип изменения
	var changeType string
	switch fileEvent.EventType {
	case "file.created", "file.uploaded", "FILE_UPLOADED", "created":
		changeType = "created"
	case "file.updated", "file.modified", "updated":
		changeType = "updated"
	case "file.deleted", "FILE_DELETED", "deleted":
		changeType = "deleted"
	default:
		changeType = "updated"
	}

	change := domain.ChangeLog{
		ID:         uuid.New(),
		ChangeID:   uuid.New().String(),
		FileID:     fileID,
		UserID:     userID,
		ChangeType: changeType,
		FilePath:   fileEvent.FilePath,
		Version:    fileEvent.Version,
		Timestamp:  fileEvent.Timestamp,
	}

	if err := s.changelogRepo.Save(ctx, &change); err != nil {
		s.logger.Error("ошибка сохранения change log", slog.Any("error", err))
		return fmt.Errorf("%s: %w", op, err)
	}

	s.logger.Info("обработано file event", slog.String("file_id", fileEvent.FileID), slog.String("event_type", fileEvent.EventType))
	return nil
}

// HandleStorageEvent обрабатывает события хранилища из Kafka
func (s *SyncService) HandleStorageEvent(ctx context.Context, msg kafkalib.Message) error {
	const op = "service.sync.HandleStorageEvent"

	var storageEvent kafka.StorageEvent
	if err := json.Unmarshal(msg.Value, &storageEvent); err != nil {
		s.logger.Error("ошибка парсинга storage event", slog.Any("error", err))
		return fmt.Errorf("%s: %w", op, err)
	}

	// Storage events обычно не требуют создания ChangeLog, но можно логировать
	s.logger.Info("обработано storage event", slog.String("file_id", storageEvent.FileID), slog.String("event_type", storageEvent.EventType))
	return nil
}

// Helper function для извлечения строк из Map
func getString(m map[string]interface{}, keys ...string) string {
	for _, key := range keys {
		if val, ok := m[key].(string); ok {
			return val
		}
	}
	return ""
}
