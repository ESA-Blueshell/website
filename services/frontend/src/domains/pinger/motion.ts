/**
 * Where a placement's box is right now. A bouncing box is sent once, as where it stood at its
 * motion epoch and how fast it goes, and every viewer works its position out from the api's clock,
 * so all of them, the pinger included, draw it in the same place. The rule mirrors the api's
 * CanvasMotion; change one, change the other.
 */
import {onBeforeUnmount, onMounted, ref, watch, type Ref} from "vue"
import type {PaintJob, Placement} from "./adapters/pinger"

export const CANVAS_W = 3840
export const CANVAS_H = 2160

/** Folds a straight run back and forth between 0 and [limit], the way a box bounces off two edges. */
export function reflect(p: number, limit: number): number {
  if (limit <= 0) return 0
  const span = 2 * limit
  const m = ((p % span) + span) % span
  return m <= limit ? m : span - m
}

export const isMoving = (p: Placement): boolean =>
  p.motion.mode === "bounce" && p.motionEpoch !== null && (p.motion.vx !== 0 || p.motion.vy !== 0)

/** The box's top-left corner at [serverNow], in canvas pixels. */
export function positionAt(p: Placement, serverNow: number): {x: number; y: number} {
  if (!isMoving(p) || p.motionEpoch === null) return {x: p.originX, y: p.originY}
  const s = (serverNow - Date.parse(p.motionEpoch)) / 1000
  return {
    x: reflect(p.originX + p.motion.vx * s, CANVAS_W - p.width),
    y: reflect(p.originY + p.motion.vy * s, CANVAS_H - p.height),
  }
}

/** How far the api's clock runs ahead of this one, read off a job as it arrives. */
export const clockOffset = (job: PaintJob, receivedAt: number = Date.now()): number =>
  job.serverTime ? Date.parse(job.serverTime) - receivedAt : 0

/**
 * The api's clock, ticking every frame while [running] holds and the tab is visible. A hidden tab
 * stops the frames; the position is worked out from the clock, so it is right again on return.
 */
export function useServerClock(running: () => boolean, offset: () => number): Readonly<Ref<number>> {
  const now = ref<number>(Date.now() + offset())
  let frame = 0

  const tick = (): void => {
    now.value = Date.now() + offset()
    frame = running() && !document.hidden ? requestAnimationFrame(tick) : 0
  }
  const kick = (): void => {
    if (!frame) tick()
  }

  watch([running, offset], kick)
  onMounted(() => {
    document.addEventListener("visibilitychange", kick)
    kick()
  })
  onBeforeUnmount(() => {
    document.removeEventListener("visibilitychange", kick)
    cancelAnimationFrame(frame)
    frame = 0
  })
  return now
}
