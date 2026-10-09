package runner

import "golang.org/x/sys/windows"

// openBrowser hands url to the shell's default handler. Not rundll32 url.dll,FileProtocolHandler:
// that is a known proxy-execution trick, and antivirus heuristics flag a binary carrying it.
func openBrowser(url string) error {
	return windows.ShellExecute(0, nil, windows.StringToUTF16Ptr(url), nil, nil, windows.SW_SHOWNORMAL)
}
