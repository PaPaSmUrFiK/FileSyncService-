package main

import (
	"errors"
	"flag"
	"fmt"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
)

func main() {
	var dbURL, migrationsPath, migrationsTable string
	flag.StringVar(&dbURL, "db-url", "", "URL базы данных (postgres://...)")
	flag.StringVar(&migrationsPath, "migrations-path", "", "путь к миграциям")
	flag.StringVar(&migrationsTable, "migrations-table", "migrations", "название таблицы миграций")
	flag.Parse()

	if dbURL == "" {
		panic("db-url обязателен")
	}
	if migrationsPath == "" {
		panic("migrations-path обязателен")
	}

	m, err := migrate.New(
		"file://"+migrationsPath,
		dbURL+"&x-migrations-table="+migrationsTable,
	)
	if err != nil {
		panic(err)
	}

	if err := m.Up(); err != nil {
		if errors.Is(err, migrate.ErrNoChange) {
			fmt.Println("нет новых миграций для применения")
			return
		}
		panic(err)
	}

	fmt.Println("миграции успешно применены")
}
