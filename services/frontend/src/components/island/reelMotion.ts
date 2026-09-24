/**
 * The flick reel's arithmetic: where every slice stands for a fractional position, and how that
 * position moves under a hand, a flick, a trackpad and the slow drift. Pure, so the component
 * only paints what this answers and the behaviour is testable without a browser.
 */

/** How wide a slice is when it is out of the middle and when it is in it, and the cut they share. */
export interface ReelShape {
  rest: number
  open: number
  cut: number
}

/** One slice, placed. */
export interface PlacedSlice {
  index: number
  /** How far from the middle it is, in slices, on the short way round. */
  offset: number
  /** 0 out of the middle, 1 in it, and everything between on the way. */
  openness: number
  left: number
  width: number
  /** 1 while drawn, falling to 0 before the slice reaches the far side where it wraps. */
  visibility: number
  layer: number
}

/** [offset] brought onto the short way round a belt of [count] slices. */
export function aroundTheBelt(offset: number, count: number): number {
  const wrapped = ((offset % count) + count) % count
  return wrapped > count / 2 ? wrapped - count : wrapped
}

/** The slice a position rests on. */
export function sliceAt(position: number, count: number): number {
  return ((Math.round(position) % count) + count) % count
}

/**
 * Every slice for [position] on a band [bandWidth] wide.
 *
 * Widths come first, from how near each slice is to the middle, and the slices are then laid end
 * to end from those widths, overlapping by the cut. That is what keeps the one growing and the
 * one shrinking joined: nothing is placed on its own. The strip is then shifted so the point
 * between the two slices around [position] sits in the middle of the band.
 */
export function placeSlices(count: number, position: number, shape: ReelShape, bandWidth: number): PlacedSlice[] {
  if (count === 0) return []
  const slices = Array.from({length: count}, (_, index) => {
    const offset = aroundTheBelt(index - position, count)
    const openness = Math.max(0, 1 - Math.abs(offset))
    return {index, offset, openness, width: shape.rest + (shape.open - shape.rest) * openness, left: 0}
  }).sort((one, other) => one.offset - other.offset)

  let run = 0
  for (const slice of slices) {
    slice.left = run
    run += slice.width - shape.cut
  }

  let before = 0
  while (before < slices.length - 1 && (slices[before + 1]?.offset ?? 1) <= 0) before++
  const earlier = slices[before] ?? slices[0]
  const later = slices[Math.min(before + 1, slices.length - 1)] ?? earlier
  const middleOf = (slice?: {left: number, width: number}) => (slice ? slice.left + slice.width / 2 : 0)
  const between = Math.min(1, Math.max(0, -(earlier?.offset ?? 0)))
  const anchor = middleOf(earlier) + between * (middleOf(later) - middleOf(earlier))
  const shift = bandWidth / 2 - anchor
  // Fading ends a little short of half the belt, so a slice is gone before it jumps sides.
  const edge = count / 2 - 1.3

  return slices.map(slice => ({
    ...slice,
    left: slice.left + shift,
    visibility: Math.min(1, Math.max(0, edge - Math.abs(slice.offset))),
    layer: Math.round(50 - Math.abs(slice.offset) * 5),
  }))
}

export interface MotionOptions {
  /** Pixels of drag that move the belt one slice. */
  unit: number
  /** Slices a second the belt drifts on its own, 0 for none. */
  drift: number
}

/** How long the belt waits after a hand lets go before it drifts again. */
export const REST_MS = 5000
/** How long a trackpad swipe may pause before the belt settles on a slice. */
export const SWIPE_SETTLE_MS = 140
const FLICK_CARRY_MS = 280
const EASE_PER_MS = 0.989
const SETTLED = 0.001
const TAP_TOLERANCE_PX = 6

/**
 * Where the belt is and where it is going.
 *
 * A drag moves it with the hand; letting go carries it on by the speed of the flick and then
 * eases onto the nearest slice. Left alone, it drifts. Every change is reported by [tick]
 * returning true, so the component paints only when something moved.
 */
export class ReelMotion {
  position: number
  private target: number | null = null
  private velocity = 0
  private dragging = false
  private moved = false
  private startX = 0
  private lastX = 0
  private lastAt = 0
  private restUntil = 0
  private settleAt: number | null = null
  hovered = false

  constructor(
    private readonly count: number,
    private readonly options: MotionOptions,
    start = 0,
  ) {
    this.position = start
  }

  /** The slice the belt rests on, or will rest on once it has settled. */
  get resting(): number {
    return sliceAt(this.target ?? this.position, this.count)
  }

  get isDragging(): boolean {
    return this.dragging
  }

  /** Advances the belt by [elapsed] milliseconds; answers whether it moved. */
  tick(elapsed: number, now: number, reduced: boolean): boolean {
    if (this.dragging) return false
    const was = this.position
    if (this.settleAt !== null && now >= this.settleAt) {
      this.settleAt = null
      this.target = Math.round(this.position)
    }
    if (this.target !== null) {
      this.position = reduced ? this.target : this.position + (this.target - this.position) * (1 - EASE_PER_MS ** elapsed)
      if (Math.abs(this.target - this.position) < SETTLED) {
        this.position = this.target
        this.target = null
      }
    } else if (this.options.drift && !this.hovered && now > this.restUntil && !reduced && this.settleAt === null) {
      this.position += this.options.drift * elapsed / 1000
    }
    return this.position !== was
  }

  press(x: number, now: number): void {
    this.dragging = true
    this.moved = false
    this.startX = x
    this.lastX = x
    this.lastAt = now
    this.velocity = 0
    this.target = null
    this.settleAt = null
  }

  drag(x: number, now: number): void {
    if (!this.dragging) return
    const dx = x - this.lastX
    this.position -= dx / this.options.unit
    this.velocity = this.velocity * 0.6 + (-dx / this.options.unit / Math.max(1, now - this.lastAt)) * 0.4
    this.lastX = x
    this.lastAt = now
    if (Math.abs(x - this.startX) > TAP_TOLERANCE_PX) this.moved = true
  }

  /** Lets go; answers whether the hand dragged at all, as opposed to pressing in place. */
  release(now: number): boolean {
    if (!this.dragging) return false
    this.dragging = false
    this.restUntil = now + REST_MS
    if (this.moved) {
      this.target = Math.round(this.position + this.velocity * FLICK_CARRY_MS)
      this.velocity = 0
    }
    return this.moved
  }

  /** A sideways trackpad swipe of [dx] pixels; the belt settles once the swipe pauses. */
  swipe(dx: number, now: number): void {
    this.position += dx / this.options.unit * 0.7
    this.target = null
    this.restUntil = now + REST_MS
    this.settleAt = now + SWIPE_SETTLE_MS
  }

  /** One slice on or back. */
  step(by: number, now: number): void {
    this.target = Math.round(this.target ?? this.position) + by
    this.restUntil = now + REST_MS
  }

  /** Brings [index] to the middle the short way round. */
  bring(index: number, now: number): void {
    const from = Math.round(this.target ?? this.position)
    this.target = from + aroundTheBelt(index - sliceAt(from, this.count), this.count)
    this.restUntil = now + REST_MS
  }
}
