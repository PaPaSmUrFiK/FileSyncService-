package main

import (
	"log"

	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/config"
	"github.com/PaPaSmUrFiK/FileSyncService-/StorageService/internal/app"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("failed to load config: %v", err)
	}

	a := app.New(cfg)
	if err := a.Run(); err != nil {
		log.Fatalf("failed to run app: %v", err)
	}
}
