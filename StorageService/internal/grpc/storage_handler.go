package grpc

import (
	"context"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/service"
	storagev1 "github.com/PaPaSmUrFiK/FileSyncService-/filesync-internal-contracts/gen/go/filesync/storage/v1"
)

type StorageHandler struct {
	storagev1.UnimplementedStorageServiceServer
	service *service.StorageService
}

func NewStorageHandler(svc *service.StorageService) *StorageHandler {
	return &StorageHandler{service: svc}
}

func (h *StorageHandler) GetUploadUrl(ctx context.Context, req *storagev1.UploadUrlRequest) (*storagev1.UrlResponse, error) {
	const op = "grpc.StorageHandler.GetUploadUrl"

	url, err := h.service.GetUploadUrl(ctx, req.FileId, req.Version, req.FileName, req.FileSize)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	return &storagev1.UrlResponse{
		Url:       url,
		Method:    "PUT",
		ExpiresIn: 3600,
	}, nil
}

func (h *StorageHandler) GetDownloadUrl(ctx context.Context, req *storagev1.DownloadUrlRequest) (*storagev1.UrlResponse, error) {
	const op = "grpc.StorageHandler.GetDownloadUrl"

	var version *int32
	if req.Version != nil {
		v := req.GetVersion()
		version = &v
	}
	url, err := h.service.GetDownloadUrl(ctx, req.FileId, version, req.FileName)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	return &storagev1.UrlResponse{
		Url:       url,
		Method:    "GET",
		ExpiresIn: 3600,
	}, nil
}

func (h *StorageHandler) DeleteFile(ctx context.Context, req *storagev1.DeleteFileRequest) (*storagev1.EmptyResponse, error) {
	const op = "grpc.StorageHandler.DeleteFile"

	var version *int32
	if req.Version != nil {
		v := req.GetVersion()
		version = &v
	}
	err := h.service.DeleteFile(ctx, req.FileId, version)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}
	return &storagev1.EmptyResponse{}, nil
}

func (h *StorageHandler) CopyFile(ctx context.Context, req *storagev1.CopyFileRequest) (*storagev1.EmptyResponse, error) {
	const op = "grpc.StorageHandler.CopyFile"

	err := h.service.CopyFile(ctx, req.SourceFileId, req.DestinationFileId)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}
	return &storagev1.EmptyResponse{}, nil
}

func (h *StorageHandler) ConfirmUpload(ctx context.Context, req *storagev1.ConfirmUploadRequest) (*storagev1.EmptyResponse, error) {
	const op = "grpc.StorageHandler.ConfirmUpload"

	// Путь должен строго совпадать с тем, что в GetUploadUrl
	storagePath := fmt.Sprintf("files/%s/v%d/data", req.FileId, req.Version)
	bucket := "file-sync-storage"

	// Мы не получаем размер в ConfirmUploadRequest, но он должен быть в базе или передан ранее.
	// Для начала сохраним хотя бы путь корректно.
	err := h.service.ConfirmUpload(ctx, req.FileId, req.Version, req.Hash, 0, storagePath, bucket)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}
	return &storagev1.EmptyResponse{}, nil
}

func (h *StorageHandler) SaveVersionMetadata(ctx context.Context, req *storagev1.SaveVersionMetadataRequest) (*storagev1.EmptyResponse, error) {
	const op = "grpc.StorageHandler.SaveVersionMetadata"

	err := h.service.SaveVersionMetadata(ctx, req.FileId, req.Version, req.StoragePath, req.Size)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}
	return &storagev1.EmptyResponse{}, nil
}
