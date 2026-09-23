import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {openLiveSocket} from "@/domains/discord/adapters/liveSocket"

vi.mock("@/services/api", () => ({apiUrl: (path: string) => `https://esa-blueshell.nl/api${path}`}))

/* A socket that records where it went and lets a test play the api's side. */
class FakeSocket {
  static opened: FakeSocket[] = []
  onmessage: ((message: {data: string}) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  close = vi.fn()
  constructor(readonly url: string) {
    FakeSocket.opened.push(this)
  }
}

describe("openLiveSocket", () => {
  beforeEach(() => {
    FakeSocket.opened = []
    vi.stubGlobal("WebSocket", FakeSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("opens the api's socket beside its REST address, and hands on each server it sends", () => {
    const live = vi.fn()
    openLiveSocket(live, vi.fn())
    const socket = FakeSocket.opened[0]

    socket.onmessage!({data: JSON.stringify({server: "Blueshell", online: 3, rooms: []})})
    socket.onmessage!({data: "not json"})

    expect(socket.url).toBe("wss://esa-blueshell.nl/api/discord/live/socket")
    expect(live).toHaveBeenCalledOnce()
    expect(live).toHaveBeenCalledWith({server: "Blueshell", online: 3, rooms: []})
  })

  it("says once that it is gone, however it went", () => {
    const gone = vi.fn()
    openLiveSocket(vi.fn(), gone)
    const socket = FakeSocket.opened[0]

    socket.onerror!()
    socket.onclose!()

    expect(gone).toHaveBeenCalledOnce()
  })

  it("closes without saying it is gone when the page closes it", () => {
    const gone = vi.fn()
    const close = openLiveSocket(vi.fn(), gone)
    const socket = FakeSocket.opened[0]

    close()
    socket.onclose!()

    expect(socket.close).toHaveBeenCalledOnce()
    expect(gone).not.toHaveBeenCalled()
  })
})
