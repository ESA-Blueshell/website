/**
 * The position rule every viewer draws a bouncing box by: a straight run folded between the canvas
 * edges, timed on the api's clock.
 */
import {describe, expect, it} from "vitest"
import {clockOffset, isMoving, positionAt, reflect, type PaintJob, type Placement} from "@/domains/pinger"

const bouncing = (over: Partial<Placement> = {}): Placement => ({
  id: 1,
  imageUrl: "/a.webp",
  originX: 100,
  originY: 200,
  width: 840,
  height: 160,
  motion: {mode: "bounce", vx: 300, vy: -100},
  motionEpoch: "2026-10-09T20:00:00Z",
  ...over,
})

const EPOCH = Date.parse("2026-10-09T20:00:00Z")

describe("reflect", () => {
  it("keeps a point inside the run where it is", () => {
    expect(reflect(0, 3000)).toBe(0)
    expect(reflect(1200, 3000)).toBe(1200)
    expect(reflect(3000, 3000)).toBe(3000)
  })

  it("folds a point past the far edge back towards the near one", () => {
    expect(reflect(3500, 3000)).toBe(2500)
    expect(reflect(6000, 3000)).toBe(0)
    expect(reflect(6500, 3000)).toBe(500)
  })

  it("folds a point before the near edge back inside", () => {
    expect(reflect(-400, 3000)).toBe(400)
    expect(reflect(-3500, 3000)).toBe(2500)
  })

  it("pins a box as wide as the canvas to the edge", () => {
    expect(reflect(250, 0)).toBe(0)
    expect(reflect(250, -10)).toBe(0)
  })
})

describe("positionAt", () => {
  it("leaves a still box at its origin", () => {
    const still = bouncing({motion: {mode: "static", vx: 300, vy: 300}})
    expect(isMoving(still)).toBe(false)
    expect(positionAt(still, EPOCH + 60_000)).toEqual({x: 100, y: 200})
  })

  it("leaves a box at its origin where the api sent no epoch", () => {
    expect(positionAt(bouncing({motionEpoch: null}), EPOCH + 5000)).toEqual({x: 100, y: 200})
  })

  it("runs a bouncing box from its origin and folds it off the edges", () => {
    const box = bouncing()
    expect(isMoving(box)).toBe(true)
    expect(positionAt(box, EPOCH)).toEqual({x: 100, y: 200})
    // Ten seconds on: x runs to 3100 and folds off 3000; y runs to -800 and folds off 0.
    expect(positionAt(box, EPOCH + 10_000)).toEqual({x: 2900, y: 800})
  })
})

describe("clockOffset", () => {
  const job: PaintJob = {prefix: null, ratePps: 1, siteCieEnabled: true, placements: [], serverTime: "2026-10-09T20:00:05Z"}

  it("reads how far the api's clock runs ahead of this one", () => {
    expect(clockOffset(job, EPOCH)).toBe(5000)
  })

  it("takes the local clock where the api sends no time", () => {
    expect(clockOffset({...job, serverTime: null}, EPOCH)).toBe(0)
  })
})
