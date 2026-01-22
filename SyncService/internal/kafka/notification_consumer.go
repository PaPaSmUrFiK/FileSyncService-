package kafka

import (
	"context"
	"encoding/json"
	"log/slog"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/websocket"
	"github.com/google/uuid"
	"github.com/segmentio/kafka-go"
)

type NotificationConsumer struct {
	reader *kafka.Reader
	hub    *websocket.Hub
	logger *slog.Logger
}

type NotificationEvent struct {
	UserID         string                 `json:"userId"`
	NotificationID string                 `json:"notificationId"`
	Type           string                 `json:"type"`
	Title          string                 `json:"title"`
	Message        string                 `json:"message"`
	Priority       string                 `json:"priority"`
	ResourceID     string                 `json:"resourceId,omitempty"`
	ResourceType   string                 `json:"resourceType,omitempty"`
	CreatedAt      string                 `json:"createdAt"`
	Data           map[string]interface{} `json:"data,omitempty"`
}

func NewNotificationConsumer(brokers []string, topic string, groupID string, hub *websocket.Hub, logger *slog.Logger) *NotificationConsumer {
	return &NotificationConsumer{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:  brokers,
			Topic:    topic,
			GroupID:  groupID,
			MinBytes: 10e3, // 10KB
			MaxBytes: 10e6, // 10MB
		}),
		hub:    hub,
		logger: logger,
	}
}

func (c *NotificationConsumer) Start(ctx context.Context) {
	c.logger.Info("starting notification consumer", slog.String("topic", c.reader.Config().Topic))
	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			c.logger.Error("failed to read kafka message", slog.Any("error", err))
			continue // With backoff?
		}

		c.processMessage(msg)
	}
}

func (c *NotificationConsumer) processMessage(msg kafka.Message) {
	var event NotificationEvent
	if err := json.Unmarshal(msg.Value, &event); err != nil {
		c.logger.Error("failed to unmarshal notification event", slog.String("value", string(msg.Value)), slog.Any("error", err))
		return
	}

	userID, err := uuid.Parse(event.UserID)
	if err != nil {
		c.logger.Error("invalid user_id in notification event", slog.String("user_id", event.UserID))
		return
	}

	c.logger.Debug("received notification event", slog.String("user_id", userID.String()), slog.String("type", event.Type))

	// Broadcast to user via WebSocket
	// We can send the whole event struct or specific payload
	c.hub.BroadcastToUser(userID, event)
}

func (c *NotificationConsumer) Close() error {
	return c.reader.Close()
}
