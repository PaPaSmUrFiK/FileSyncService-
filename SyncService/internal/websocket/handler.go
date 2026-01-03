package websocket

import (
	"log/slog"
	"net/http"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/service"
)

type Handler struct {
	hub           *Hub
	deviceService *service.DeviceService
	logger        *slog.Logger
}

func NewHandler(hub *Hub, deviceService *service.DeviceService, logger *slog.Logger) *Handler {
	return &Handler{
		hub:           hub,
		deviceService: deviceService,
		logger:        logger,
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	const op = "websocket.Handler.ServeHTTP"

	// Extract device_id from URL or token
	// For simplicity, let's assume it's in the query for now, or we get it from token
	token := r.Header.Get("Authorization")
	if len(token) > 7 && token[:7] == "Bearer " {
		token = token[7:]
	} else {
		token = r.URL.Query().Get("token")
	}

	if token == "" {
		http.Error(w, "не авторизован", http.StatusUnauthorized)
		return
	}

	ctx := r.Context()
	device, err := h.deviceService.Authenticate(ctx, token)
	if err != nil {
		h.logger.Warn("ошибка аутентификации при подключении WS", slog.Any("error", err))
		http.Error(w, "неверный токен", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		h.logger.Error("ошибка апгрейда до WebSocket", slog.Any("error", err))
		return
	}

	client := &Client{
		Hub:      h.hub,
		Conn:     conn,
		DeviceID: device.ID,
		send:     make(chan []byte, 256),
		logger:   h.logger,
	}
	client.Hub.register <- client

	go client.WritePump()
	go client.ReadPump()
}
