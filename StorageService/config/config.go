package config

import (
	"fmt"
	"os"

	"github.com/ilyakaznacheev/cleanenv"
)

type Config struct {
	Env      string `yaml:"env" env-default:"local"`
	GRPC     GRPCConfig `yaml:"grpc"`
	Postgres PostgresConfig `yaml:"postgres"`
	Minio    MinioConfig `yaml:"minio"`
	Kafka    KafkaConfig `yaml:"kafka"`
}

type GRPCConfig struct {
	Port int `yaml:"port" env-default:"9094"`
}

type PostgresConfig struct {
	URL string `yaml:"url" env:"DB_URL" env-default:"postgres://filesync:secret@localhost:5433/postgres?sslmode=disable"`
}

type MinioConfig struct {
	Endpoint  string `yaml:"endpoint" env:"MINIO_ENDPOINT" env-default:"localhost:9000"`
	AccessKey string `yaml:"access_key" env:"MINIO_ACCESS_KEY" env-default:"minioadmin"`
	SecretKey string `yaml:"secret_key" env:"MINIO_SECRET_KEY" env-default:"minioadmin"`
	Bucket    string `yaml:"bucket" env:"MINIO_BUCKET" env-default:"file-sync-storage"`
	UseSSL    bool   `yaml:"use_ssl" env:"MINIO_USE_SSL" env-default:"false"`
}

type KafkaConfig struct {
	Brokers []string `yaml:"brokers" env:"KAFKA_BROKERS" env-default:"localhost:9092"`
	Topic   string   `yaml:"topic" env:"KAFKA_TOPIC" env-default:"storage.events"`
}

func Load() (*Config, error) {
	var cfg Config

	configPath := os.Getenv("CONFIG_PATH")
	if configPath != "" {
		if _, err := os.Stat(configPath); os.IsNotExist(err) {
			return nil, fmt.Errorf("config file not found: %s", configPath)
		}

		if err := cleanenv.ReadConfig(configPath, &cfg); err != nil {
			return nil, fmt.Errorf("failed to read config: %w", err)
		}
	} else {
		// Try to read from local config.yaml if it exists
		if _, err := os.Stat("config/config.yaml"); err == nil {
			if err := cleanenv.ReadConfig("config/config.yaml", &cfg); err != nil {
				return nil, fmt.Errorf("failed to read local config: %w", err)
			}
		} else {
			// Just read from env
			if err := cleanenv.ReadEnv(&cfg); err != nil {
				return nil, fmt.Errorf("failed to read env: %w", err)
			}
		}
	}

	return &cfg, nil
}
