import {describe, expect, it} from "vitest"
import {DriftMotion, PASS_MS} from "@/components/island/driftMotion"
import {REST_MS} from "@/components/island/reelMotion"

const laidOut = (loop = 1000) => {
  const motion = new DriftMotion()
  motion.loop = loop
  return motion
}

describe("DriftMotion", () => {
  it("drifts one pass per PASS_MS and wraps where the second pass begins", () => {
    const motion = laidOut()

    expect(motion.tick(PASS_MS / 4, 0, true)).toBe(true)
    expect(motion.offset).toBeCloseTo(250)
    motion.tick(PASS_MS, 0, true)
    expect(motion.offset).toBeCloseTo(250)
  })

  it("stands still when held, when drift is not allowed, and before it is laid out", () => {
    const held = laidOut()
    held.held = true
    expect(held.tick(16, 0, true)).toBe(false)
    expect(laidOut().tick(16, 0, false)).toBe(false)
    expect(new DriftMotion().wantsFrames(0, true)).toBe(false)
  })

  it("follows a drag with the hand and says the press was a drag", () => {
    const motion = laidOut()
    motion.press(500, 0)
    motion.drag(480, 10)
    motion.drag(460, 20)

    expect(motion.offset).toBe(40)
    expect(motion.wantsFrames(20, true)).toBe(false)
    expect(motion.tick(16, 20, true)).toBe(false)
    expect(motion.release(20)).toBe(true)
  })

  it("carries a flick on, slowing, and rests before drifting again", () => {
    const motion = laidOut()
    motion.press(500, 0)
    motion.drag(400, 10)
    motion.drag(300, 20)
    motion.release(20)

    const before = motion.offset
    expect(motion.tick(16, 36, true)).toBe(true)
    expect(motion.offset).toBeGreaterThan(before)
    for (let at = 0; at < 200; at++) motion.tick(16, 36, true)
    expect(motion.wantsFrames(36, true)).toBe(false)
    expect(motion.wantsFrames(20 + REST_MS, true)).toBe(true)
  })

  it("throws nothing when the finger stood still before lifting, or never moved past the slop", () => {
    const stood = laidOut()
    stood.press(500, 0)
    stood.drag(300, 10)
    expect(stood.release(200)).toBe(true)
    expect(stood.wantsFrames(200, false)).toBe(false)

    const tapped = laidOut()
    tapped.press(500, 0)
    tapped.drag(495, 10)
    expect(tapped.release(12)).toBe(false)
    expect(tapped.release(12)).toBe(false)
  })

  it("scrolls by a sideways swipe, wrapping backwards too", () => {
    const motion = laidOut()
    motion.swipe(-100, 0)

    expect(motion.offset).toBe(900)
    expect(motion.wantsFrames(0, true)).toBe(false)
  })

  it("ignores a drag with no press", () => {
    const motion = laidOut()
    motion.drag(100, 0)
    expect(motion.offset).toBe(0)
  })
})
