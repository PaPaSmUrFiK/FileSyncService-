package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/segmentio/kafka-go"
)

type StorageEvent struct {
	EventType   string    `json:"event_type"` // stored|deleted|moved
	FileID      string    `json:"file_id"`
	Version     int32     `json:"version"`
	StoragePath string    `json:"storage_path"`
	Bucket      string    `json:"bucket"`
	Size        int64     `json:"size"`
	Hash        string    `json:"hash"`
	Timestamp   time.Time `json:"timestamp"`
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

func (p *Producer) PublishEvent(ctx context.Context, event StorageEvent) error {
	const op = "kafka.Producer.PublishEvent"

	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("%s: ошибка маршалинга события: %w", op, err)
	}

	err = p.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(event.FileID),
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
