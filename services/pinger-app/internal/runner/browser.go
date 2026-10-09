package runner

import (
	"os"
	"os/exec"
	"runtime"
)

// openBrowser launches the per-OS system browser at url, returning any error so the caller can
// print the url to open by hand. Under sudo it hands the open to SUDO_USER, so it lands in the
// member's own default browser and session, not root's, which the OAuth loopback needs.
func openBrowser(url string) error {
	cmd, args := openCommand(url)
	if sudoUser := os.Getenv("SUDO_USER"); sudoUser != "" {
		args = append([]string{"-u", sudoUser, cmd}, args...)
		cmd = "sudo"
	}
	return exec.Command(cmd, args...).Start()
}

func openCommand(url string) (string, []string) {
	switch runtime.GOOS {
	case "darwin":
		return "open", []string{url}
	case "windows":
		return "rundll32", []string{"url.dll,FileProtocolHandler", url}
	default:
		return "xdg-open", []string{url}
	}
}
