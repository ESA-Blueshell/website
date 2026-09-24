import {describe, expect, it} from "vitest"
import {aroundTheBelt, placeSlices, ReelMotion, REST_MS, sliceAt, SWIPE_SETTLE_MS} from "@/components/island/reelMotion"

const SHAPE = {rest: 240, open: 608, cut: 30}
const BAND = 1440

/** The overlap between each slice and the next, left to right, for the slices on the band. */
function overlaps(position: number, count = 13): number[] {
  const drawn = placeSlices(count, position, SHAPE, BAND)
    .filter(slice => slice.visibility > 0)
    .sort((one, other) => one.left - other.left)
  return drawn.slice(1).map((slice, at) => Math.round(drawn[at].left + drawn[at].width - slice.left))
}

describe("placeSlices", () => {
  it("opens the slice in the middle and centres it on the band", () => {
    const slices = placeSlices(13, 4, SHAPE, BAND)
    const open = slices.find(slice => slice.index === 4)!

    expect(open.openness).toBe(1)
    expect(open.width).toBe(608)
    expect(open.left + open.width / 2).toBe(BAND / 2)
    expect(slices.filter(slice => slice.openness > 0)).toHaveLength(1)
  })

  it("keeps every slice joined to the next while one grows and the other shrinks", () => {
    for (const position of [0, 0.25, 0.5, 0.75, 3.1, 12.9]) {
      expect(overlaps(position)).toEqual(overlaps(position).map(() => SHAPE.cut))
    }
  })

  it("shares the opening between the two slices either side of a half-way position", () => {
    const slices = placeSlices(13, 2.5, SHAPE, BAND)

    expect(slices.find(slice => slice.index === 2)!.openness).toBeCloseTo(0.5)
    expect(slices.find(slice => slice.index === 3)!.openness).toBeCloseTo(0.5)
  })

  it("fades a slice out before it reaches the far side, where it jumps round the belt", () => {
    const slices = placeSlices(13, 0, SHAPE, BAND)
    const far = slices.find(slice => Math.abs(slice.offset) === 6)!

    expect(far.visibility).toBe(0)
    expect(slices.find(slice => slice.index === 0)!.visibility).toBe(1)
  })

  it("places nothing on an empty belt, and the front slice highest", () => {
    expect(placeSlices(0, 0, SHAPE, BAND)).toEqual([])
    const [front] = placeSlices(3, 1, SHAPE, BAND).sort((one, other) => other.layer - one.layer)
    expect(front.index).toBe(1)
  })
})

describe("belt arithmetic", () => {
  it("measures offsets the short way round and names the slice a position rests on", () => {
    expect(aroundTheBelt(12, 13)).toBe(-1)
    expect(aroundTheBelt(-12, 13)).toBe(1)
    expect(sliceAt(-1, 13)).toBe(12)
    expect(sliceAt(13.4, 13)).toBe(0)
  })
})

describe("ReelMotion", () => {
  const run = (motion: ReelMotion, from: number, ms: number, reduced = false) => {
    let moved = false
    for (let at = from; at < from + ms; at += 16) moved = motion.tick(16, at, reduced) || moved
    return moved
  }

  it("follows a hand, then carries a flick on and settles on a slice", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0})
    motion.press(700, 0)
    motion.drag(400, 16)
    expect(motion.position).toBeCloseTo(1)
    expect(motion.tick(16, 20, false)).toBe(false)

    expect(motion.release(32)).toBe(true)
    run(motion, 32, 3000)

    expect(Number.isInteger(motion.position)).toBe(true)
    expect(motion.position).toBeGreaterThan(1)
  })

  it("reads a press in place as a press, not a drag", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0})
    motion.press(500, 0)
    motion.drag(503, 10)

    expect(motion.release(20)).toBe(false)
    expect(motion.release(30)).toBe(false)
    motion.drag(900, 40)
    expect(motion.position).toBeCloseTo(-0.01)
  })

  it("steps one slice either way and brings a slice to the middle the short way round", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0})
    motion.step(1, 0)
    motion.step(1, 0)
    expect(motion.resting).toBe(2)

    motion.bring(12, 0)
    run(motion, 0, 3000)
    expect(motion.position).toBe(-1)
    expect(motion.resting).toBe(12)
  })

  it("settles a trackpad swipe once it pauses", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0.5})
    motion.swipe(260, 0)
    expect(motion.position).toBeCloseTo(0.6067, 3)

    run(motion, 0, SWIPE_SETTLE_MS + 2000)
    expect(motion.position).toBe(1)
  })

  it("drifts when left alone, waits after a hand, and stops under the pointer", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0.25})
    expect(run(motion, 0, 1000)).toBe(true)
    expect(motion.position).toBeGreaterThan(0.2)

    motion.press(0, 1000)
    motion.release(1000)
    const held = motion.position
    run(motion, 1000, REST_MS - 100)
    expect(motion.position).toBe(held)

    motion.hovered = true
    expect(run(motion, 1000 + REST_MS + 1, 1000)).toBe(false)
  })

  it("jumps straight to where it is going and never drifts with reduced motion", () => {
    const motion = new ReelMotion(13, {unit: 300, drift: 0.25})
    expect(run(motion, 0, 1000, true)).toBe(false)

    motion.step(1, 0)
    expect(motion.tick(16, 16, true)).toBe(true)
    expect(motion.position).toBe(1)
    expect(motion.isDragging).toBe(false)
  })
})
