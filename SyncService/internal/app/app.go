package app

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/config"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/lib/logger/handlers/slogpretty"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/websocket"
)

type App struct {
	cfg    *config.Config
	logger *slog.Logger
}

func New(cfg *config.Config) *App {
	logger := setupLogger(cfg.Env)
	return &App{
		cfg:    cfg,
		logger: logger,
	}
}

func (a *App) Run() error {
	const op = "app.Run"
	ctx := context.Background()

	// 1. WebSocket Hub
	hub := websocket.NewHub(a.logger)
	go hub.Run(ctx)

	// 2. Kafka Consumer for Notifications
	notificationConsumer := kafka.NewNotificationConsumer(
		a.cfg.Kafka.Brokers,
		a.cfg.Kafka.Topics.SyncNotifications,
		a.cfg.Kafka.ConsumerGroup,
		hub,
		a.logger,
	)

	// Start consumer in a goroutine
	go notificationConsumer.Start(ctx)
	defer notificationConsumer.Close()

	// 3. WebSocket HTTP Server
	wsHandler := websocket.NewHandler(hub, a.cfg.JWTSecret, a.logger)
	http.Handle("/ws", wsHandler) // WebSocket endpoint for notifications

	go func() {
		addr := fmt.Sprintf(":%d", a.cfg.HTTP.Port)
		a.logger.Info("WebSocket сервер запущен", slog.Int("port", a.cfg.HTTP.Port))
		if err := http.ListenAndServe(addr, nil); err != nil {
			a.logger.Error("ошибка при запуске WebSocket сервера", slog.Any("error", err))
			os.Exit(1)
		}
	}()

	a.logger.Info("Sync Service (Notification Gateway) запущен", slog.Int("http_port", a.cfg.HTTP.Port))

	// Wait for termination signal
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	a.logger.Info("остановка Sync Service...")
	return nil
}

func setupLogger(env string) *slog.Logger {
	var log *slog.Logger

	switch env {
	case "local":
		log = setupPrettySlog()
	case "dev":
		log = slog.New(
			slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelDebug}),
		)
	case "prod":
		log = slog.New(
			slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}),
		)
	default:
		log = slog.New(
			slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}),
		)
	}

	return log
}

func setupPrettySlog() *slog.Logger {
	opts := slogpretty.PrettyHandlerOptions{
		SlogOpts: &slog.HandlerOptions{
			Level: slog.LevelDebug,
		},
	}

	handler := opts.NewPrettyHandler(os.Stdout)

	return slog.New(handler)
}
