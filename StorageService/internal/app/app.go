package app

import (
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/config"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/grpc"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/kafka"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/lib/logger/handlers/slogpretty"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/repository/postgres"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/service"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/storage/minio"
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

	// 1. Database
	db, err := sqlx.Connect("pgx", a.cfg.Postgres.URL)
	if err != nil {
		return fmt.Errorf("%s: не удалось подключиться к postgres: %w", op, err)
	}
	defer db.Close()

	// 2. Repository
	repo := postgres.NewStorageRepository(db)

	// 3. Minio
	minioClient, err := minio.NewMinioClient(
		a.cfg.Minio.Endpoint,
		a.cfg.Minio.AccessKey,
		a.cfg.Minio.SecretKey,
		a.cfg.Minio.Bucket,
		a.cfg.Minio.UseSSL,
	)
	if err != nil {
		return fmt.Errorf("%s: не удалось инициализировать minio: %w", op, err)
	}

	// 4. Kafka
	producer := kafka.NewProducer(a.cfg.Kafka.Brokers, a.cfg.Kafka.Topic)
	defer producer.Close()

	// 5. Service
	svc := service.NewStorageService(repo, minioClient, producer)

	// 6. gRPC Server
	handler := grpc.NewStorageHandler(svc)
	server := grpc.NewServer(a.cfg.GRPC.Port, handler, a.logger)

	go func() {
		if err := server.Start(); err != nil {
			a.logger.Error("ошибка при запуске gRPC сервера", slog.Any("error", err))
			os.Exit(1)
		}
	}()

	a.logger.Info("Storage Service запущен", slog.Int("port", a.cfg.GRPC.Port))

	// Wait for termination signal
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	a.logger.Info("остановка Storage Service...")
	server.Stop()
	a.logger.Info("Storage Service остановлен")

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
