package grpc

import (
	"fmt"
	"log/slog"
	"net"

	storagev1 "github.com/PaPaSmUrFiK/FileSyncService-/filesync-internal-contracts/gen/go/filesync/storage/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

type Server struct {
	grpcServer *grpc.Server
	port       int
	logger     *slog.Logger
}

func NewServer(port int, handler *StorageHandler, logger *slog.Logger) *Server {
	s := grpc.NewServer()
	storagev1.RegisterStorageServiceServer(s, handler)
	reflection.Register(s)

	return &Server{
		grpcServer: s,
		port:       port,
		logger:     logger,
	}
}

func (s *Server) Start() error {
	const op = "grpc.Server.Start"

	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", s.port))
	if err != nil {
		return fmt.Errorf("%s: не удалось открыть порт: %w", op, err)
	}

	s.logger.Info("gRPC сервер запущен", slog.Int("port", s.port))
	if err := s.grpcServer.Serve(lis); err != nil {
		return fmt.Errorf("%s: ошибка при работе сервера: %w", op, err)
	}

	return nil
}

func (s *Server) Stop() {
	s.grpcServer.GracefulStop()
}
