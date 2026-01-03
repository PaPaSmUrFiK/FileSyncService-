package main

import (
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/config"
	"github.com/PaPaSmUrFiK/FileSyncService-/SyncService/internal/app"
)

func main() {
	cfg := config.MustLoad()
	application := app.New(cfg)

	if err := application.Run(); err != nil {
		panic(err)
	}
}
