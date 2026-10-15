package server

import (
	"strings"
	"testing"
	"time"
)

func env(values map[string]string) func(string) string {
	return func(name string) string { return values[name] }
}

func TestDefaultsSuitAPodOfOneGibibyte(t *testing.T) {
	srv, err := FromEnv(env(nil), quiet)
	if err != nil {
		t.Fatal(err)
	}
	if srv.Addr != ":8080" || srv.WriteTimeout != 90*time.Second {
		t.Fatalf("listens on %q, writes for %v", srv.Addr, srv.WriteTimeout)
	}
}

func TestSettingsAreRead(t *testing.T) {
	srv, err := FromEnv(env(map[string]string{
		"IMAGING_LISTEN":               "127.0.0.1:9000",
		"IMAGING_MAX_BYTES":            "100",
		"IMAGING_MAX_PIXELS":           "100",
		"IMAGING_MAX_FRAMES":           "10",
		"IMAGING_MAX_ANIMATION_PIXELS": "1000",
		"IMAGING_WAIT_SECONDS":         "5",
		"IMAGING_CONCURRENCY":          "2",
	}), quiet)
	if err != nil {
		t.Fatal(err)
	}
	if srv.Addr != "127.0.0.1:9000" || srv.WriteTimeout != 65*time.Second {
		t.Fatalf("listens on %q, writes for %v", srv.Addr, srv.WriteTimeout)
	}
}

func TestSettingThatIsNotAPositiveNumberStopsTheStart(t *testing.T) {
	for _, value := range []string{"lots", "0", "-3"} {
		_, err := FromEnv(env(map[string]string{"IMAGING_CONCURRENCY": value}), quiet)
		if err == nil || !strings.Contains(err.Error(), "IMAGING_CONCURRENCY") {
			t.Errorf("%q: got %v", value, err)
		}
	}
}
