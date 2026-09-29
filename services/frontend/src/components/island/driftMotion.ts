import {DRAG} from "./dragAxis"
import {REST_MS} from "./reelMotion"

/** How long the row takes to drift one full pass on its own. */
export const PASS_MS = 60_000
/** The share of a thrown row's speed kept per millisecond as it coasts. */
const COAST_PER_MS = 0.995
/** Below this speed, in pixels per millisecond, a coasting row has stopped. */
const STILL = 0.02
/** A finger that stood this long before lifting has thrown nothing. */
const HELD_MS = 80

/**
 * Where a looping row stands, in pixels travelled leftwards within one pass.
 *
 * Left alone it drifts; a drag moves it with the hand and a flick throws it on, and a sideways
 * trackpad swipe scrolls it. After a hand lets go it waits a while before drifting again, as the
 * reel does. [tick] answers whether it moved, so the row writes only frames that change it.
 */
export class DriftMotion {
  offset = 0
  /** The width of one pass, which the offset wraps at; 0 until the row is laid out. */
  loop = 0
  /** Set while the pointer rests on the row or a tile holds focus, so it can be read. */
  held = false
  private pressed = false
  private moved = false
  private startX = 0
  private lastX = 0
  private lastAt = 0
  private velocity = 0
  private restUntil = 0

  /** Whether the row will move of its own accord from [now] on, and so wants frames. */
  wantsFrames(now: number, drifts: boolean): boolean {
    if (this.pressed) return false
    if (Math.abs(this.velocity) >= STILL) return true
    return drifts && !this.held && this.loop > 0 && now >= this.restUntil
  }

  tick(elapsed: number, now: number, drifts: boolean): boolean {
    if (this.pressed) return false
    if (Math.abs(this.velocity) >= STILL) {
      this.move(this.velocity * elapsed)
      this.velocity *= COAST_PER_MS ** elapsed
      return true
    }
    this.velocity = 0
    if (!this.wantsFrames(now, drifts)) return false
    this.move(this.loop / PASS_MS * elapsed)
    return true
  }

  press(x: number, now: number): void {
    this.pressed = true
    this.moved = false
    this.startX = x
    this.lastX = x
    this.lastAt = now
    this.velocity = 0
  }

  drag(x: number, now: number): void {
    if (!this.pressed) return
    const dx = x - this.lastX
    this.move(-dx)
    this.velocity = this.velocity * 0.6 + (-dx / Math.max(1, now - this.lastAt)) * 0.4
    this.lastX = x
    this.lastAt = now
    if (Math.abs(x - this.startX) > DRAG.slop) this.moved = true
  }

  /** Lets go; answers whether the hand dragged, as opposed to pressing a tile in place. */
  release(now: number): boolean {
    if (!this.pressed) return false
    this.pressed = false
    this.restUntil = now + REST_MS
    if (!this.moved || now - this.lastAt > HELD_MS) this.velocity = 0
    return this.moved
  }

  /** A sideways trackpad swipe of [dx] pixels. */
  swipe(dx: number, now: number): void {
    this.move(dx)
    this.velocity = 0
    this.restUntil = now + REST_MS
  }

  private move(by: number): void {
    this.offset += by
    if (this.loop > 0) this.offset = ((this.offset % this.loop) + this.loop) % this.loop
  }
}
