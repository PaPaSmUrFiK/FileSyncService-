package config

import (
	"fmt"
	"os"

	"github.com/ilyakaznacheev/cleanenv"
)

type Config struct {
	Env      string         `yaml:"env" env:"ENV" env-default:"local"`
	GRPC     GRPCConfig     `yaml:"grpc"`
	HTTP     HTTPConfig     `yaml:"http"`
	Postgres PostgresConfig `yaml:"postgres"`
	Kafka    KafkaConfig    `yaml:"kafka"`
}

type GRPCConfig struct {
	Port int `yaml:"port" env:"GRPC_PORT" env-default:"9095"`
}

type HTTPConfig struct {
	Port int `yaml:"port" env:"HTTP_PORT" env-default:"8084"`
}

type PostgresConfig struct {
	URL string `yaml:"url" env:"DB_URL" env-required:"true"`
}

type KafkaConfig struct {
	Brokers       []string    `yaml:"brokers" env:"KAFKA_BROKERS" env-separator:","`
	ConsumerGroup string      `yaml:"consumer_group" env:"KAFKA_CONSUMER_GROUP" env-default:"sync-service"`
	Topics        KafkaTopics `yaml:"topics"`
}

type KafkaTopics struct {
	FileEvents    string `yaml:"file_events" env:"KAFKA_TOPIC_FILE_EVENTS" env-default:"file.events"`
	StorageEvents string `yaml:"storage_events" env:"KAFKA_TOPIC_STORAGE_EVENTS" env-default:"storage.events"`
	SyncEvents    string `yaml:"sync_events" env:"KAFKA_TOPIC_SYNC_EVENTS" env-default:"sync.events"`
}

func MustLoad() *Config {
	configPath := os.Getenv("CONFIG_PATH")
	if configPath == "" {
		configPath = "config/config.yaml"
	}

	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		panic(fmt.Sprintf("config file does not exist: %s", configPath))
	}

	var cfg Config
	if err := cleanenv.ReadConfig(configPath, &cfg); err != nil {
		panic(fmt.Sprintf("cannot read config: %s", err))
	}

	return &cfg
}
