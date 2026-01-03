package kafka

import (
	"context"
	"log/slog"
	"time"

	"github.com/segmentio/kafka-go"
)

type FileEvent struct {
	EventType string    `json:"event_type"`
	FileID    string    `json:"file_id"`
	UserID    string    `json:"user_id"`
	Version   int32     `json:"version"`
	FileName  string    `json:"file_name"`
	FilePath  string    `json:"file_path"`
	Size      int64     `json:"size"`
	Hash      string    `json:"hash"`
	Timestamp time.Time `json:"timestamp"`
}

type StorageEvent struct {
	EventType   string    `json:"event_type"`
	FileID      string    `json:"file_id"`
	Version     int32     `json:"version"`
	StoragePath string    `json:"storage_path"`
	Bucket      string    `json:"bucket"`
	Size        int64     `json:"size"`
	Hash        string    `json:"hash"`
	Timestamp   time.Time `json:"timestamp"`
}

type Consumer struct {
	reader *kafka.Reader
	logger *slog.Logger
}

func NewConsumer(brokers []string, topic string, groupID string, logger *slog.Logger) *Consumer {
	return &Consumer{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:  brokers,
			Topic:    topic,
			GroupID:  groupID,
			MinBytes: 10e3, // 10KB
			MaxBytes: 10e6, // 10MB
		}),
		logger: logger,
	}
}

func (c *Consumer) Consume(ctx context.Context, handler func(msg kafka.Message) error) {
	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			c.logger.Error("ошибка при чтении сообщения из kafka", slog.Any("error", err))
			continue
		}

		if err := handler(msg); err != nil {
			c.logger.Error("ошибка при обработке сообщения kafka", slog.Any("error", err))
		}
	}
}

func (c *Consumer) Close() error {
	return c.reader.Close()
}
