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
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/grpc"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/lib/logger/handlers/slogpretty"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/repository/postgres"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/service"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/websocket"
	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/jmoiron/sqlx"
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

	// 1. Database
	db, err := sqlx.Connect("pgx", a.cfg.Postgres.URL)
	if err != nil {
		return fmt.Errorf("%s: не удалось подключиться к postgres: %w", op, err)
	}
	defer db.Close()

	// 2. Repositories
	deviceRepo := postgres.NewDeviceRepository(db)
	syncStateRepo := postgres.NewSyncStateRepository(db)
	changelogRepo := postgres.NewChangeLogRepository(db)
	conflictRepo := postgres.NewConflictRepository(db)

	// 3. Kafka
	producer := kafka.NewProducer(a.cfg.Kafka.Brokers, a.cfg.Kafka.Topics.SyncEvents)
	defer producer.Close()

	// 4. WebSocket Hub
	hub := websocket.NewHub(a.logger)
	go hub.Run(ctx)

	// 5. Services
	deviceService := service.NewDeviceService(deviceRepo)
	changelogService := service.NewChangelogService(changelogRepo)
	conflictService := service.NewConflictService(conflictRepo)
	syncService := service.NewSyncService(deviceRepo, syncStateRepo, changelogRepo, conflictRepo, producer, a.logger)

	// 6. gRPC Server
	grpcHandler := grpc.NewSyncHandler(deviceService, syncService, changelogService, conflictService)
	grpcServer := grpc.NewServer(a.cfg.GRPC.Port, grpcHandler, a.logger)

	go func() {
		if err := grpcServer.Start(); err != nil {
			a.logger.Error("ошибка при запуске gRPC сервера", slog.Any("error", err))
			os.Exit(1)
		}
	}()

	// 8. WebSocket HTTP Server
	wsHandler := websocket.NewHandler(hub, deviceService, a.logger)
	http.Handle("/ws/sync", wsHandler)

	go func() {
		addr := fmt.Sprintf(":%d", a.cfg.HTTP.Port)
		a.logger.Info("WebSocket сервер запущен", slog.Int("port", a.cfg.HTTP.Port))
		if err := http.ListenAndServe(addr, nil); err != nil {
			a.logger.Error("ошибка при запуске WebSocket сервера", slog.Any("error", err))
			os.Exit(1)
		}
	}()

	a.logger.Info("Sync Service запущен", slog.Int("grpc_port", a.cfg.GRPC.Port), slog.Int("http_port", a.cfg.HTTP.Port))

	// Wait for termination signal
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	a.logger.Info("остановка Sync Service...")
	grpcServer.Stop()
	a.logger.Info("Sync Service остановлен")

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
