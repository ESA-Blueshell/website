/**
 * Pinger domain adapter: the only file in this domain that imports from @/services/api
 * (frontend ADR-002). Everything else imports from here.
 */
import {paint, setPaint, uploadPublicImage, FileType, type Image, type PaintRequest, type PaintResponse} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type Saved} from "@/utils/refusals"

export type {Refused}
export type {PaintRequest}
export type PaintJob = PaintResponse

const {refusable} = refusalReader({})

/** The seeded defaults, used until the api answers and when it cannot be read. */
export const DEFAULT_PAINT: PaintJob = {
  prefix: null,
  ratePps: 128,
  originX: 1470,
  originY: 180,
  width: 900,
  height: 720,
  imageUrl: null,
}

/** The current paint job, or the defaults if it cannot be read. The read is public, so this rarely fails. */
export const loadPaintJob = (): Promise<PaintJob> => readOr(paint(), DEFAULT_PAINT)

export const savePaintJob = (job: PaintRequest): Promise<Saved<PaintJob> | Refused> =>
  refusable(setPaint({body: job}), "The paint job could not be saved.")

/** Stores an image an admin chose and hands back its stored path, to put on the paint job. */
export async function storePaintImage(file: File): Promise<Saved<string> | Refused> {
  const stored = await refusable(uploadPublicImage({query: {type: FileType.PINGER_PAINT}, body: {file}}), "That image could not be stored.")
  return stored.ok ? {ok: true, saved: (stored.saved as Image).path} : stored
}
