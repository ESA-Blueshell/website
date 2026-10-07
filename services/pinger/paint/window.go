package paint

import (
	"time"
	_ "time/tzdata"
)

// Window is the span the event accepts pings in; the sender stays silent outside it.
type Window struct {
	Start, End time.Time
}

// EventWindow is SNTPings 2026: Friday 9 October 18:00 to the end of Sunday 11 October, Amsterdam time.
func EventWindow() Window {
	ams, err := time.LoadLocation("Europe/Amsterdam")
	if err != nil {
		panic(err)
	}
	return Window{
		Start: time.Date(2026, 10, 9, 18, 0, 0, 0, ams),
		End:   time.Date(2026, 10, 12, 0, 0, 0, 0, ams),
	}
}

func (w Window) Contains(t time.Time) bool {
	return !t.Before(w.Start) && t.Before(w.End)
}
