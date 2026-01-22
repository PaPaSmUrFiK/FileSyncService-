package websocket

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"

	"github.com/google/uuid"
)

type Hub struct {
	// Registered clients, map UserID -> list of clients (to support multiple connections/tabs)
	clients    map[uuid.UUID]map[*Client]bool
	register   chan *Client
	unregister chan *Client
	broadcast  chan []byte // Broadcast to all connected users (optional)

	// Channel for sending message to specific users
	userMessages chan *UserMessage

	mu     sync.RWMutex
	logger *slog.Logger
}

type UserMessage struct {
	UserID  uuid.UUID
	Payload []byte
}

func NewHub(logger *slog.Logger) *Hub {
	return &Hub{
		clients:      make(map[uuid.UUID]map[*Client]bool),
		register:     make(chan *Client),
		unregister:   make(chan *Client),
		broadcast:    make(chan []byte),
		userMessages: make(chan *UserMessage),
		logger:       logger,
	}
}

func (h *Hub) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return

		case client := <-h.register:
			h.mu.Lock()
			if h.clients[client.UserID] == nil {
				h.clients[client.UserID] = make(map[*Client]bool)
			}
			h.clients[client.UserID][client] = true
			h.mu.Unlock()
			h.logger.Debug("client connected", slog.String("user_id", client.UserID.String()))

		case client := <-h.unregister:
			h.mu.Lock()
			if userClients, ok := h.clients[client.UserID]; ok {
				if _, ok := userClients[client]; ok {
					delete(userClients, client)
					close(client.send)
				}
				if len(userClients) == 0 {
					delete(h.clients, client.UserID)
				}
			}
			h.mu.Unlock()
			h.logger.Debug("client disconnected", slog.String("user_id", client.UserID.String()))

		case message := <-h.broadcast:
			h.mu.RLock()
			for _, userClients := range h.clients {
				for client := range userClients {
					select {
					case client.send <- message:
					default:
						// Buffer full, close connection (or just skip)
						// For now, skipping to avoid blocking Hub
						h.logger.Warn("client buffer full, skipping broadcast message", slog.String("user_id", client.UserID.String()))
					}
				}
			}
			h.mu.RUnlock()

		case userMsg := <-h.userMessages:
			h.mu.RLock()
			if userClients, ok := h.clients[userMsg.UserID]; ok {
				for client := range userClients {
					select {
					case client.send <- userMsg.Payload:
					default:
						h.logger.Warn("client buffer full, skipping user message", slog.String("user_id", userMsg.UserID.String()))
					}
				}
			}
			h.mu.RUnlock()
		}
	}
}

// BroadcastToUser sends a JSON payload to a specific user
func (h *Hub) BroadcastToUser(userID uuid.UUID, payload interface{}) {
	jsonPayload, err := json.Marshal(payload)
	if err != nil {
		h.logger.Error("failed to marshal payload", slog.Any("error", err))
		return
	}

	select {
	case h.userMessages <- &UserMessage{UserID: userID, Payload: jsonPayload}:
	default:
		h.logger.Warn("userMessages channel full, dropping message", slog.String("user_id", userID.String()))
	}
}
