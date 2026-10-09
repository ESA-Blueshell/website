/**
 * Pinger domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  apiUrl,
  addPlacement as addPlacementApi,
  movePlacement as movePlacementApi,
  paint,
  removePlacement as removePlacementApi,
  setPlacementMotion,
  setSettings,
  uploadPublicImage,
  FileType,
  type Image,
  type MotionResponse,
  type PaintResponse,
  type PaintSettingsRequest,
  type PlacementBoxRequest,
  type PlacementResponse,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type Saved} from "@/utils/refusals"

export type {Refused}

/** A placement's motion; the speeds are canvas pixels per second. */
export type Motion = MotionResponse
export type MotionMode = Motion["mode"]

/**
 * A placement whose box is where it stood at [motionEpoch]; a bouncing one has moved on since.
 * The epoch and the job's server time are null where an api from before motion sends neither.
 */
export type Placement = Omit<PlacementResponse, "motionEpoch"> & {motionEpoch: string | null}
export type PaintJob = Omit<PaintResponse, "placements" | "serverTime"> & {placements: Placement[]; serverTime: string | null}

type WirePlacement = Omit<PlacementResponse, "motion" | "motionEpoch"> & Partial<Pick<PlacementResponse, "motion" | "motionEpoch">>
type WireJob = Omit<PaintResponse, "placements" | "serverTime"> & {placements: WirePlacement[]; serverTime?: string}
export type PaintSettings = PaintSettingsRequest
/** A placement's box, the shape both a move and the box half of an add carry. */
export type Box = PlacementBoxRequest

const {refusable} = refusalReader({})

const STILL: Motion = {mode: "static", vx: 0, vy: 0}

/** A placement the api sent without motion stands still. */
const toPlacement = (wire: WirePlacement): Placement =>
  ({...wire, motion: wire.motion ?? STILL, motionEpoch: wire.motionEpoch ?? null})

const toJob = (wire: WireJob): PaintJob =>
  ({...wire, placements: wire.placements.map(toPlacement), serverTime: wire.serverTime ?? null})

/** The seeded defaults, used until the api answers and when it cannot be read. */
export const DEFAULT_PAINT: PaintJob = {
  prefix: null,
  ratePps: 128,
  siteCieEnabled: true,
  placements: [],
  serverTime: null,
}

const readPaintJob = async (): Promise<PaintJob | null> => {
  const wire = await readOr(paint(), null)
  return wire ? toJob(wire) : null
}

/** The current paint job, or the defaults if it cannot be read. The read is public, so this rarely fails. */
export const loadPaintJob = async (): Promise<PaintJob> => (await readPaintJob()) ?? DEFAULT_PAINT

const POLL_MS = 2000
const STREAM_RETRY_MS = 30_000

/**
 * Hands [onJob] the paint job on connect and on every change, and returns the close handle the
 * caller runs on unmount. Where the stream fails it polls the job every two seconds and tries the
 * stream again every thirty, so an api without the stream still moves the boxes.
 */
export function openPaintStream(onJob: (job: PaintJob) => void): () => void {
  let source: EventSource | null = null
  let poll = 0
  let retry = 0
  let closed = false

  const stopPolling = (): void => {
    window.clearInterval(poll)
    window.clearTimeout(retry)
    poll = 0
    retry = 0
  }

  const connect = (): void => {
    retry = 0
    source = new EventSource(apiUrl("/pinger/paint/stream"))
    source.onmessage = (event: MessageEvent<string>) => {
      stopPolling()
      // A malformed frame is dropped: the job already painted stands until the next one.
      try {
        onJob(toJob(JSON.parse(event.data) as WireJob))
      } catch {
        // Nothing to paint from a frame that does not parse.
      }
    }
    source.onerror = () => {
      source?.close()
      source = null
      if (!poll) {
        poll = window.setInterval(async () => {
          const job = await readPaintJob()
          if (job && !closed) onJob(job)
        }, POLL_MS)
      }
      if (!retry) retry = window.setTimeout(connect, STREAM_RETRY_MS)
    }
  }

  connect()
  return () => {
    closed = true
    source?.close()
    stopPolling()
  }
}

/** Saves the settings the whole canvas shares: the prefix, the rate and the SiteCie toggle. */
export const saveSettings = async (settings: PaintSettings): Promise<Saved<PaintJob> | Refused> => {
  const result = await refusable(setSettings({body: settings}), "The settings could not be saved.")
  return result.ok ? {ok: true, saved: toJob(result.saved)} : result
}

const asPlacement = (result: Saved<WirePlacement> | Refused): Saved<Placement> | Refused =>
  result.ok ? {ok: true, saved: toPlacement(result.saved)} : result

/** Adds an image to the canvas in its own box, answering with the new placement or the refusal. */
export const addPlacement = (imagePath: string, box: Box): Promise<Saved<Placement> | Refused> =>
  refusable(addPlacementApi({body: {imagePath, ...box}}), "That image could not be added to the canvas.").then(asPlacement)

/** Moves or resizes one placement's box. */
export const movePlacement = (id: number, box: Box): Promise<Saved<Placement> | Refused> =>
  refusable(movePlacementApi({path: {id}, body: box}), "That placement could not be moved.").then(asPlacement)

/** Sets how one placement moves; the api starts the new motion from where the box is now. */
export const setMotion = (id: number, motion: Motion): Promise<Saved<Placement> | Refused> =>
  refusable(setPlacementMotion({path: {id}, body: motion}), "That placement's motion could not be saved.").then(asPlacement)

/** Removes one placement from the canvas. */
export const removePlacement = (id: number): Promise<Saved<void> | Refused> =>
  refusable(removePlacementApi({path: {id}}), "That placement could not be removed.")

/** Stores an image an admin chose and hands back its stored path, to put on a placement. */
export async function storePaintImage(file: File): Promise<Saved<string> | Refused> {
  const stored = await refusable(uploadPublicImage({query: {type: FileType.PINGER_PAINT}, body: {file}}), "That image could not be stored.")
  return stored.ok ? {ok: true, saved: (stored.saved as Image).path} : stored
}
