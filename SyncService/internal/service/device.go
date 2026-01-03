package service

import (
	"context"
	"fmt"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/domain"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository"
	"github.com/google/uuid"
)

type DeviceService struct {
	deviceRepo repository.DeviceRepository
}

func NewDeviceService(deviceRepo repository.DeviceRepository) *DeviceService {
	return &DeviceService{
		deviceRepo: deviceRepo,
	}
}

func (s *DeviceService) RegisterDevice(ctx context.Context, userID uuid.UUID, name, deviceType, os, osVersion string) (*domain.Device, error) {
	const op = "service.device.RegisterDevice"

	token := uuid.New().String() // Simple token for now
	device := &domain.Device{
		UserID:     userID,
		DeviceName: name,
		DeviceType: deviceType,
		OS:         os,
		OSVersion:  osVersion,
		SyncToken:  token,
		IsActive:   true,
	}

	if err := s.deviceRepo.Register(ctx, device); err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}

	return device, nil
}

func (s *DeviceService) Authenticate(ctx context.Context, token string) (*domain.Device, error) {
	const op = "service.device.Authenticate"

	device, err := s.deviceRepo.GetByToken(ctx, token)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", op, err)
	}
	if device == nil || !device.IsActive {
		return nil, fmt.Errorf("%s: устройство не найдено или неактивно", op)
	}

	return device, nil
}
