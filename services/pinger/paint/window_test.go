package paint

import (
	"testing"
	"time"
)

func TestEventWindowRunsFromFridayEveningToSundayMidnightInAmsterdam(t *testing.T) {
	ams, err := time.LoadLocation("Europe/Amsterdam")
	if err != nil {
		t.Fatal(err)
	}
	w := EventWindow()

	cases := []struct {
		at   time.Time
		open bool
	}{
		{time.Date(2026, 10, 9, 17, 59, 59, 0, ams), false},
		{time.Date(2026, 10, 9, 18, 0, 0, 0, ams), true},
		{time.Date(2026, 10, 9, 16, 0, 0, 0, time.UTC), true},
		{time.Date(2026, 10, 11, 23, 59, 59, 0, ams), true},
		{time.Date(2026, 10, 12, 0, 0, 0, 0, ams), false},
	}
	for _, c := range cases {
		if got := w.Contains(c.at); got != c.open {
			t.Errorf("%s: open %v, want %v", c.at, got, c.open)
		}
	}
}
