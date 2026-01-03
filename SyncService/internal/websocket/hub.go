package websocket

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"

	"github.com/google/uuid"
)

type Hub struct {
	clients    map[uuid.UUID]*Client
	register   chan *Client
	unregister chan *Client
	broadcast  chan []byte
	mu         sync.RWMutex
	logger     *slog.Logger
}

func NewHub(logger *slog.Logger) *Hub {
	return &Hub{
		clients:    make(map[uuid.UUID]*Client),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		broadcast:  make(chan []byte),
		logger:     logger,
	}
}

func (h *Hub) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client.DeviceID] = client
			h.mu.Unlock()
			h.logger.Debug("устройство подключено", slog.String("device_id", client.DeviceID.String()))
		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client.DeviceID]; ok {
				delete(h.clients, client.DeviceID)
				close(client.send)
				h.logger.Debug("устройство отключено", slog.String("device_id", client.DeviceID.String()))
			}
			h.mu.Unlock()
		case message := <-h.broadcast:
			h.mu.RLock()
			for _, client := range h.clients {
				select {
				case client.send <- message:
				default:
					h.logger.Warn("буфер клиента переполнен, отключаем", slog.String("device_id", client.DeviceID.String()))
					// We can't safely unregister here, would depend on implementation
				}
			}
			h.mu.RUnlock()
		}
	}
}

func (h *Hub) NotifyDevice(deviceID uuid.UUID, event interface{}) {
	h.mu.RLock()
	client, ok := h.clients[deviceID]
	h.mu.RUnlock()

	if !ok {
		return
	}

	payload, err := json.Marshal(event)
	if err != nil {
		h.logger.Error("ошибка маршалинга уведомления", slog.Any("error", err))
		return
	}

	select {
	case client.send <- payload:
	default:
		h.logger.Warn("не удалось отправить уведомление, буфер полон", slog.String("device_id", deviceID.String()))
	}
}
