package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/segmentio/kafka-go"
)

type SyncEvent struct {
	EventType      string                 `json:"event_type"` // sync.started|sync.completed|sync.failed|conflict.detected|conflict.resolved
	SyncID         string                 `json:"sync_id"`
	UserID         string                 `json:"user_id"`
	DeviceID       string                 `json:"device_id"`
	FileIDs        []string               `json:"file_ids"`
	ChangesCount   int                    `json:"changes_count"`
	ConflictsCount int                    `json:"conflicts_count"`
	Timestamp      time.Time              `json:"timestamp"`
	Metadata       map[string]interface{} `json:"metadata"`
}

type Producer struct {
	writer *kafka.Writer
}

func NewProducer(brokers []string, topic string) *Producer {
	return &Producer{
		writer: &kafka.Writer{
			Addr:     kafka.TCP(brokers...),
			Topic:    topic,
			Balancer: &kafka.LeastBytes{},
		},
	}
}

func (p *Producer) PublishSyncEvent(ctx context.Context, event SyncEvent) error {
	const op = "kafka.Producer.PublishSyncEvent"

	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("%s: ошибка маршалинга события синхронизации: %w", op, err)
	}

	err = p.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(event.SyncID),
		Value: payload,
	})
	if err != nil {
		return fmt.Errorf("%s: ошибка записи сообщения в kafka: %w", op, err)
	}

	return nil
}

func (p *Producer) Close() error {
	return p.writer.Close()
}
