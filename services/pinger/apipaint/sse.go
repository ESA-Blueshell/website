package apipaint

import (
	"bufio"
	"bytes"
	"io"
)

// readEvents reads a server-sent event stream until it ends, calling alive on every line, the
// heartbeat comments included, and event with each event's joined data. It returns the read error,
// or io.ErrUnexpectedEOF when the server closed a stream that should stay open.
func readEvents(body io.Reader, alive func(), event func(data []byte)) error {
	sc := bufio.NewScanner(body)
	// A descriptor with many placements outgrows the scanner's 64 KiB default line.
	sc.Buffer(make([]byte, 0, 64<<10), 4<<20)
	var data []byte
	for sc.Scan() {
		alive()
		line := sc.Bytes()
		switch {
		case len(line) == 0:
			if data == nil {
				continue
			}
			event(data)
			data = nil
		case bytes.HasPrefix(line, []byte("data:")):
			if data != nil {
				data = append(data, '\n')
			}
			data = append(data, bytes.TrimPrefix(bytes.TrimPrefix(line, []byte("data:")), []byte(" "))...)
		}
	}
	if err := sc.Err(); err != nil {
		return err
	}
	return io.ErrUnexpectedEOF
}
