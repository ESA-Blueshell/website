package runner

import (
	"os/exec"
	"runtime"
)

// openBrowser launches the system browser at url. The command is per-OS; a failure is returned so
// the caller can print the url for the member to open by hand.
func openBrowser(url string) error {
	var cmd string
	var args []string
	switch runtime.GOOS {
	case "darwin":
		cmd, args = "open", []string{url}
	case "windows":
		cmd, args = "rundll32", []string{"url.dll,FileProtocolHandler", url}
	default:
		cmd, args = "xdg-open", []string{url}
	}
	return exec.Command(cmd, args...).Start()
}
