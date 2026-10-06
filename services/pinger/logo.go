// Package pinger holds the logo the service paints.
package pinger

import _ "embed"

// Logo is the Blueshell logo without the esports line, on a transparent background.
//
//go:embed logo.png
var Logo []byte
