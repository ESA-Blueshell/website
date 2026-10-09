/**
 * The pinger paint adapter: each write wraps its SDK call as a saved value or a refusal, the image
 * store hands back the stored path, and the paint stream falls back to polling where it fails.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {
  addPlacement,
  loadPaintJob,
  movePlacement,
  openPaintStream,
  removePlacement,
  saveSettings,
  setMotion,
  storePaintImage,
  DEFAULT_PAINT,
  type PaintJob,
} from "@/domains/pinger"

const {mockPaint, mockSetSettings, mockAdd, mockMove, mockRemove, mockUpload, mockMotion} = vi.hoisted(() => ({
  mockMotion: vi.fn(),
  mockPaint: vi.fn(),
  mockSetSettings: vi.fn(),
  mockAdd: vi.fn(),
  mockMove: vi.fn(),
  mockRemove: vi.fn(),
  mockUpload: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    paint: mockPaint,
    setSettings: mockSetSettings,
    addPlacement: mockAdd,
    movePlacement: mockMove,
    removePlacement: mockRemove,
    uploadPublicImage: mockUpload,
    setPlacementMotion: mockMotion,
  }
})

class FakeEventSource {
  public static instances: FakeEventSource[] = []
  public onmessage: ((event: MessageEvent<string>) => void) | null = null
  public onerror: (() => void) | null = null
  public closed = false

  public constructor(public url: string) {
    FakeEventSource.instances.push(this)
  }

  public close(): void {
    this.closed = true
  }

  public send(data: string): void {
    this.onmessage?.(new MessageEvent("message", {data}))
  }
}

const streamed = {
  prefix: null,
  ratePps: 128,
  siteCieEnabled: true,
  serverTime: "2026-10-09T20:00:00Z",
  placements: [{
    id: 3, imageUrl: "/a.webp", originX: 10, originY: 20, width: 100, height: 50,
    motion: {mode: "bounce", vx: 240, vy: 160}, motionEpoch: "2026-10-09T19:59:00Z",
  }],
}

describe("the pinger paint adapter", () => {
  beforeEach(() => vi.clearAllMocks())

  it("falls back to the seeded defaults when the paint job cannot be read", async () => {
    mockPaint.mockResolvedValue({error: {detail: "down"}, data: null})
    expect(await loadPaintJob()).toEqual(DEFAULT_PAINT)
  })

  it("saves the settings, answering with the new paint job", async () => {
    const job = {prefix: null, ratePps: 200, siteCieEnabled: false, placements: []}
    mockSetSettings.mockResolvedValue({status: 200, data: job})

    const result = await saveSettings({ratePps: 200, siteCieEnabled: false})

    expect(mockSetSettings).toHaveBeenCalledWith({body: {ratePps: 200, siteCieEnabled: false}})
    expect(result).toEqual({ok: true, saved: {...job, serverTime: null}})
  })

  it("adds a placement from an image path and a box", async () => {
    const placement = {id: 7, imageUrl: "/u.webp", originX: 0, originY: 0, width: 10, height: 10}
    mockAdd.mockResolvedValue({status: 200, data: placement})

    const result = await addPlacement("pinger-paint/u.webp", {originX: 0, originY: 0, width: 10, height: 10})

    expect(mockAdd).toHaveBeenCalledWith({body: {imagePath: "pinger-paint/u.webp", originX: 0, originY: 0, width: 10, height: 10}})
    expect(result).toEqual({ok: true, saved: {...placement, motion: {mode: "static", vx: 0, vy: 0}, motionEpoch: null}})
  })

  it("moves a placement's box", async () => {
    mockMove.mockResolvedValue({status: 200, data: {id: 7}})

    await movePlacement(7, {originX: 1, originY: 2, width: 3, height: 4})

    expect(mockMove).toHaveBeenCalledWith({path: {id: 7}, body: {originX: 1, originY: 2, width: 3, height: 4}})
  })

  it("surfaces a refusal rather than a saved value", async () => {
    mockRemove.mockResolvedValue({error: {detail: "nope"}, data: null})

    const result = await removePlacement(7)

    expect(result.ok).toBe(false)
  })

  it("stores an image and hands back its path", async () => {
    mockUpload.mockResolvedValue({status: 200, data: {path: "pinger-paint/stored.webp"}})

    const result = await storePaintImage(new File(["x"], "art.png", {type: "image/png"}))

    expect(result).toEqual({ok: true, saved: "pinger-paint/stored.webp"})
  })

  it("passes a store refusal straight through", async () => {
    mockUpload.mockResolvedValue({error: {detail: "too big"}, data: null})

    const result = await storePaintImage(new File(["x"], "art.png", {type: "image/png"}))

    expect(result.ok).toBe(false)
  })

  it("sets a placement's motion through the motion endpoint", async () => {
    mockMotion.mockResolvedValue({status: 200, data: streamed.placements[0]})

    const result = await setMotion(3, {mode: "bounce", vx: 240, vy: 160})

    expect(mockMotion).toHaveBeenCalledWith({path: {id: 3}, body: {mode: "bounce", vx: 240, vy: 160}})
    expect(result).toEqual({ok: true, saved: streamed.placements[0]})
  })

  describe("the paint stream", () => {
    beforeEach(() => {
      FakeEventSource.instances = []
      vi.stubGlobal("EventSource", FakeEventSource)
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
      vi.unstubAllGlobals()
    })

    it("hands on each streamed job, drops a bad frame, and closes on the handle", () => {
      const seen: PaintJob[] = []
      const close = openPaintStream(job => seen.push(job))
      const source = FakeEventSource.instances[0]

      expect(source.url).toContain("/pinger/paint/stream")
      source.send(JSON.stringify(streamed))
      source.send("not json")
      expect(seen).toEqual([streamed])

      close()
      expect(source.closed).toBe(true)
    })

    it("polls the job every two seconds once the stream fails, and stops once it comes back", async () => {
      mockPaint.mockResolvedValue({status: 200, data: {...streamed, serverTime: undefined, placements: []}})
      const seen: PaintJob[] = []
      const close = openPaintStream(job => seen.push(job))
      FakeEventSource.instances[0].onerror?.()
      expect(FakeEventSource.instances[0].closed).toBe(true)

      await vi.advanceTimersByTimeAsync(4000)
      expect(mockPaint).toHaveBeenCalledTimes(2)
      expect(seen.at(-1)).toEqual({...streamed, serverTime: null, placements: []})

      await vi.advanceTimersByTimeAsync(26_000)
      const retried = FakeEventSource.instances[1]
      expect(retried).toBeDefined()
      retried.send(JSON.stringify(streamed))
      const polls = mockPaint.mock.calls.length
      await vi.advanceTimersByTimeAsync(10_000)
      expect(mockPaint).toHaveBeenCalledTimes(polls)

      close()
      expect(retried.closed).toBe(true)
    })

    it("skips a poll the api refuses rather than painting the defaults", async () => {
      mockPaint.mockResolvedValue({error: {detail: "down"}, data: null})
      const seen: PaintJob[] = []
      const close = openPaintStream(job => seen.push(job))
      FakeEventSource.instances[0].onerror?.()

      await vi.advanceTimersByTimeAsync(2000)
      expect(mockPaint).toHaveBeenCalledOnce()
      expect(seen).toEqual([])

      close()
      await vi.advanceTimersByTimeAsync(60_000)
      expect(mockPaint).toHaveBeenCalledOnce()
      expect(FakeEventSource.instances).toHaveLength(1)
    })
  })
})
