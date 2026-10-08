/**
 * The pinger paint adapter: each write wraps its SDK call as a saved value or a refusal, and the
 * image store hands back the stored path.
 */
import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addPlacement,
  loadPaintJob,
  movePlacement,
  removePlacement,
  saveSettings,
  storePaintImage,
  DEFAULT_PAINT,
} from "@/domains/pinger"

const {mockPaint, mockSetSettings, mockAdd, mockMove, mockRemove, mockUpload} = vi.hoisted(() => ({
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
  }
})

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
    expect(result).toEqual({ok: true, saved: job})
  })

  it("adds a placement from an image path and a box", async () => {
    const placement = {id: 7, imageUrl: "/u.webp", originX: 0, originY: 0, width: 10, height: 10}
    mockAdd.mockResolvedValue({status: 200, data: placement})

    const result = await addPlacement("pinger-paint/u.webp", {originX: 0, originY: 0, width: 10, height: 10})

    expect(mockAdd).toHaveBeenCalledWith({body: {imagePath: "pinger-paint/u.webp", originX: 0, originY: 0, width: 10, height: 10}})
    expect(result).toEqual({ok: true, saved: placement})
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
})
