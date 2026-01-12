package service

import (
	"context"
	"fmt"
	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/repository"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/storage/minio"
	"github.com/google/uuid"
)

type StorageService struct {
	repo          repository.StorageRepository
	storageClient *minio.Client
	producer      *kafka.Producer
}

func NewStorageService(repo repository.StorageRepository, storageClient *minio.Client, producer *kafka.Producer) *StorageService {
	return &StorageService{
		repo:          repo,
		storageClient: storageClient,
		producer:      producer,
	}
}

func (s *StorageService) GetUploadUrl(ctx context.Context, fileID string, version int32, fileName string, size int64) (string, error) {
	const op = "service.storage.GetUploadUrl"

	// Используем стабильный путь, который можно восстановить в ConfirmUpload
	objectName := fmt.Sprintf("files/%s/v%d/data", fileID, version)
	u, err := s.storageClient.GetPresignedUploadURL(ctx, objectName, 1*time.Hour)
	if err != nil {
		return "", fmt.Errorf("%s: не удалось получить URL для загрузки: %w", op, err)
	}
	return u.String(), nil
}

func (s *StorageService) GetDownloadUrl(ctx context.Context, fileID string, version *int32, fileName string) (string, error) {
	const op = "service.storage.GetDownloadUrl"

	uid, err := uuid.Parse(fileID)
	if err != nil {
		return "", fmt.Errorf("%s: неверный ID файла: %w", op, err)
	}

	var storagePath string
	if version != nil {
		v, err := s.repo.GetVersion(ctx, uid, *version)
		if err != nil {
			return "", fmt.Errorf("%s: ошибка при получении версии: %w", op, err)
		}
		if v == nil {
			return "", fmt.Errorf("%s: версия не найдена", op)
		}
		storagePath = v.StoragePath
	} else {
		mapping, err := s.repo.GetMappingByFileID(ctx, uid)
		if err != nil {
			return "", fmt.Errorf("%s: ошибка при получении маппинга: %w", op, err)
		}
		if mapping == nil {
			return "", fmt.Errorf("%s: маппинг файла не найден", op)
		}
		storagePath = mapping.StoragePath
	}

	u, err := s.storageClient.GetPresignedDownloadURL(ctx, storagePath, fileName, 1*time.Hour)
	if err != nil {
		return "", fmt.Errorf("%s: не удалось получить URL для скачивания: %w", op, err)
	}
	return u.String(), nil
}

func (s *StorageService) ConfirmUpload(ctx context.Context, fileID string, version int32, hash string, size int64, storagePath string, bucket string) error {
	const op = "service.storage.ConfirmUpload"

	uid, err := uuid.Parse(fileID)
	if err != nil {
		return fmt.Errorf("%s: неверный ID файла: %w", op, err)
	}

	// Save to storage_mappings (latest)
	mapping := &domain.StorageMapping{
		FileID:      uid,
		StoragePath: storagePath,
		Bucket:      bucket,
		Size:        size,
		Hash:        hash,
	}
	if err := s.repo.SaveMapping(ctx, mapping); err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}

	// Save to storage_versions
	v := &domain.StorageVersion{
		FileID:      uid,
		Version:     version,
		StoragePath: storagePath,
		Bucket:      bucket,
		Size:        size,
	}
	if err := s.repo.SaveVersion(ctx, v); err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}

	// Publish event
	event := kafka.StorageEvent{
		EventType:   "stored",
		FileID:      fileID,
		Version:     version,
		StoragePath: storagePath,
		Bucket:      bucket,
		Size:        size,
		Hash:        hash,
		Timestamp:   time.Now(),
	}
	return s.producer.PublishEvent(ctx, event)
}

func (s *StorageService) DeleteFile(ctx context.Context, fileID string, version *int32) error {
	const op = "service.storage.DeleteFile"

	uid, err := uuid.Parse(fileID)
	if err != nil {
		return fmt.Errorf("%s: неверный ID файла: %w", op, err)
	}

	if version != nil {
		v, err := s.repo.GetVersion(ctx, uid, *version)
		if err != nil {
			return fmt.Errorf("%s: %w", op, err)
		}
		if v != nil {
			if err := s.storageClient.DeleteObject(ctx, v.StoragePath); err != nil {
				return fmt.Errorf("%s: не удалось удалить объект из хранилища: %w", op, err)
			}
			if err := s.repo.DeleteVersion(ctx, uid, *version); err != nil {
				return fmt.Errorf("%s: %w", op, err)
			}
		}
	} else {
		versions, err := s.repo.GetVersionsByFileID(ctx, uid)
		if err != nil {
			return fmt.Errorf("%s: %w", op, err)
		}
		for _, v := range versions {
			_ = s.storageClient.DeleteObject(ctx, v.StoragePath)
		}
		if err := s.repo.DeleteAllVersions(ctx, uid); err != nil {
			return fmt.Errorf("%s: %w", op, err)
		}
		if err := s.repo.DeleteMapping(ctx, uid); err != nil {
			return fmt.Errorf("%s: %w", op, err)
		}
	}

	// Publish event
	event := kafka.StorageEvent{
		EventType: "deleted",
		FileID:    fileID,
		Timestamp: time.Now(),
	}
	if version != nil {
		event.Version = *version
	}
	return s.producer.PublishEvent(ctx, event)
}

func (s *StorageService) CopyFile(ctx context.Context, srcFileID, destFileID string) error {
	const op = "service.storage.CopyFile"

	srcUID, err := uuid.Parse(srcFileID)
	if err != nil {
		return fmt.Errorf("%s: неверный ID исходного файла: %w", op, err)
	}

	mapping, err := s.repo.GetMappingByFileID(ctx, srcUID)
	if err != nil {
		return fmt.Errorf("%s: %w", op, err)
	}
	if mapping == nil {
		return fmt.Errorf("%s: маппинг исходного файла не найден", op)
	}

	destObjectName := fmt.Sprintf("files/%s/v1/copy", destFileID)
	if err := s.storageClient.CopyObject(ctx, mapping.StoragePath, destObjectName); err != nil {
		return fmt.Errorf("%s: не удалось скопировать объект: %w", op, err)
	}

	return s.ConfirmUpload(ctx, destFileID, 1, mapping.Hash, mapping.Size, destObjectName, mapping.Bucket)
}
