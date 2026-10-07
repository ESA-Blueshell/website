// Command pinger-app is the member's desktop pinger: it signs in through the site over the OAuth
// loopback flow, paints the api-owned paint job with the shared engine, and reports the member's
// status so their contribution climbs on the leaderboard. This is the walking skeleton — auth,
// paint and report with minimal chrome.
package main

import (
	"embed"
	"log"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"

	"github.com/ESA-Blueshell/website/services/pinger-app/internal/config"
	"github.com/ESA-Blueshell/website/services/pinger-app/internal/runner"
)

//go:embed all:frontend/dist
var assets embed.FS

func main() {
	r, err := runner.New(config.BaseURL())
	if err != nil {
		log.Fatalln("start:", err)
	}
	app := NewApp(r)

	if err := wails.Run(&options.App{
		Title:     "Blueshell Pinger",
		Width:     360,
		Height:    480,
		OnStartup: app.startup,
		AssetServer: &assetserver.Options{
			Assets: assets,
		},
		Bind: []any{app},
	}); err != nil {
		log.Fatalln("run:", err)
	}
}
