package grpc

import (
	"context"

	"time"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/service"
	syncv1 "github.com/PaPaSmUrFiK/FileSyncService-/filesync-internal-contracts/gen/go/filesync/sync/v1"
	"github.com/google/uuid"
)

type SyncHandler struct {
	syncv1.UnimplementedSyncServiceServer
	deviceService    *service.DeviceService
	syncService      *service.SyncService
	changelogService *service.ChangelogService
	conflictService  *service.ConflictService
}

func NewSyncHandler(
	deviceService *service.DeviceService,
	syncService *service.SyncService,
	changelogService *service.ChangelogService,
	conflictService *service.ConflictService,
) *SyncHandler {
	return &SyncHandler{
		deviceService:    deviceService,
		syncService:      syncService,
		changelogService: changelogService,
		conflictService:  conflictService,
	}
}

func (h *SyncHandler) RegisterDevice(ctx context.Context, req *syncv1.RegisterDeviceRequest) (*syncv1.DeviceResponse, error) {
	userID, err := uuid.Parse(req.UserId)
	if err != nil {
		return nil, err
	}

	device, err := h.deviceService.RegisterDevice(ctx, userID, req.DeviceName, req.DeviceType, req.Os, req.OsVersion)
	if err != nil {
		return nil, err
	}

	return &syncv1.DeviceResponse{
		DeviceId:     device.ID.String(),
		SyncToken:    device.SyncToken,
		RegisteredAt: device.RegisteredAt.Format(time.RFC3339),
	}, nil
}

func (h *SyncHandler) PushChanges(ctx context.Context, req *syncv1.PushChangesRequest) (*syncv1.PushChangesResponse, error) {
	deviceID, err := uuid.Parse(req.DeviceId)
	if err != nil {
		return nil, err
	}

	changes := make([]domain.ChangeLog, len(req.Changes))
	for i, c := range req.Changes {
		fileID, _ := uuid.Parse(c.FileId)
		timestamp, _ := time.Parse(time.RFC3339, c.ClientTimestamp)
		changes[i] = domain.ChangeLog{
			FileID:     fileID,
			ChangeType: c.ChangeType,
			FilePath:   c.FilePath,
			FileHash:   c.FileHash,
			FileSize:   c.FileSize,
			Version:    c.LocalVersion,
			Timestamp:  timestamp,
		}
	}

	if err := h.syncService.PushChanges(ctx, deviceID, changes); err != nil {
		return nil, err
	}

	// For simplicity, return success for all changes
	results := make([]*syncv1.ChangeResult, len(req.Changes))
	for i, c := range req.Changes {
		results[i] = &syncv1.ChangeResult{
			FileId: c.FileId,
			Status: "success",
		}
	}

	return &syncv1.PushChangesResponse{
		Results:    results,
		SyncCursor: uuid.New().String(),
	}, nil
}

func (h *SyncHandler) PullChanges(ctx context.Context, req *syncv1.PullChangesRequest) (*syncv1.PullChangesResponse, error) {
	deviceID, err := uuid.Parse(req.DeviceId)
	if err != nil {
		return nil, err
	}

	changes, err := h.syncService.PullChanges(ctx, deviceID)
	if err != nil {
		return nil, err
	}

	protoChanges := make([]*syncv1.FileChangeInfo, len(changes))
	for i, c := range changes {
		protoChanges[i] = &syncv1.FileChangeInfo{
			FileId:     c.FileID.String(),
			ChangeType: c.ChangeType,
			FilePath:   c.FilePath,
			FileName:   c.FilePath, // Simplified
			FileSize:   c.FileSize,
			FileHash:   c.FileHash,
			Version:    c.Version,
			Timestamp:  c.Timestamp.Format(time.RFC3339),
		}
	}

	return &syncv1.PullChangesResponse{
		Changes:    protoChanges,
		SyncCursor: uuid.New().String(),
		HasMore:    false,
	}, nil
}
