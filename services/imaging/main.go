// The imaging service, which decodes uploads away from the api (architecture ADR-014).
package main

import (
	"log/slog"
	"os"

	"github.com/ESA-Blueshell/website/services/imaging/internal/server"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	srv, err := server.FromEnv(os.Getenv, log)
	if err != nil {
		log.Error("could not start", "error", err)
		os.Exit(1)
	}
	log.Error("stopped", "error", srv.ListenAndServe())
	os.Exit(1)
}
