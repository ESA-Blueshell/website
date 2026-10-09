/**
 * Pinger domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {
  addPlacement as addPlacementApi,
  movePlacement as movePlacementApi,
  paint,
  removePlacement as removePlacementApi,
  setSettings,
  uploadPublicImage,
  FileType,
  type Image,
  type PaintResponse,
  type PaintSettingsRequest,
  type PlacementBoxRequest,
  type PlacementResponse,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type Saved} from "@/utils/refusals"

export type {Refused}
export type PaintJob = PaintResponse
export type Placement = PlacementResponse
export type PaintSettings = PaintSettingsRequest
/** A placement's box, the shape both a move and the box half of an add carry. */
export type Box = PlacementBoxRequest

const {refusable} = refusalReader({})

/** The seeded defaults, used until the api answers and when it cannot be read. */
export const DEFAULT_PAINT: PaintJob = {
  prefix: null,
  ratePps: 128,
  siteCieEnabled: true,
  placements: [],
}

/** The current paint job, or the defaults if it cannot be read. The read is public, so this rarely fails. */
export const loadPaintJob = (): Promise<PaintJob> => readOr(paint(), DEFAULT_PAINT)

/** Saves the settings the whole canvas shares: the prefix, the rate and the SiteCie toggle. */
export const saveSettings = (settings: PaintSettings): Promise<Saved<PaintJob> | Refused> =>
  refusable(setSettings({body: settings}), "The settings could not be saved.")

/** Adds an image to the canvas in its own box, answering with the new placement or the refusal. */
export const addPlacement = (imagePath: string, box: Box): Promise<Saved<Placement> | Refused> =>
  refusable(addPlacementApi({body: {imagePath, ...box}}), "That image could not be added to the canvas.")

/** Moves or resizes one placement's box. */
export const movePlacement = (id: number, box: Box): Promise<Saved<Placement> | Refused> =>
  refusable(movePlacementApi({path: {id}, body: box}), "That placement could not be moved.")

/** Removes one placement from the canvas. */
export const removePlacement = (id: number): Promise<Saved<void> | Refused> =>
  refusable(removePlacementApi({path: {id}}), "That placement could not be removed.")

/** Stores an image an admin chose and hands back its stored path, to put on a placement. */
export async function storePaintImage(file: File): Promise<Saved<string> | Refused> {
  const stored = await refusable(uploadPublicImage({query: {type: FileType.PINGER_PAINT}, body: {file}}), "That image could not be stored.")
  return stored.ok ? {ok: true, saved: (stored.saved as Image).path} : stored
}
