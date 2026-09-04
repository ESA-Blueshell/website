/**
 * How a picture is handed to the markup that draws it.
 *
 * The api answers with widths and urls; the string a browser reads is composed here, because which
 * widths a browser is offered is a display decision and belongs where the markup is written. The
 * companion decision — `sizes`, how wide the picture will actually be drawn — is layout knowledge
 * and stays in the component that owns the layout. Nothing here knows what the picture is of, so
 * nothing here imports a domain or the generated client: every picture the site draws is the same
 * four numbers and a url (frontend ADR-001).
 */

/**
 * An image a page draws: where it is served, how large it is, and the widths it is stored at.
 *
 * Structural rather than the generated `Image`, so that the island states the shape it needs
 * and every domain's adapter satisfies it by answering with what the api already sends
 * (frontend ADR-002).
 */
export interface Picture {
  /** Where it is stored, which is what a save points at to put it on a record. */
  path: string
  /** Where the full-size image is served. */
  url: string
  /** How wide it is, absent where its size could not be read. */
  width?: number | null
  /** How tall it is, absent where its size could not be read. */
  height?: number | null
  /** The widths it is stored at, narrowest first. */
  renditions: {url: string; width: number}[]
}

/**
 * How a picker is told to store the bytes somebody chose.
 *
 * The picker asks rather than knows: which endpoint takes the file and what kind of picture it
 * is are the caller's business, and a shared control that reached for either would be a shared
 * control that belongs to one domain. A refusal comes back in words, because a picture the
 * converter cannot read is the one thing whoever chose it can act on.
 */
export type PictureStore = (file: File) => Promise<{ok: true; picture: Picture} | {ok: false; reason: string}>

/**
 * The `srcset` for a picture, or nothing where there is only one of it.
 *
 * The full-size picture is offered alongside the stored widths, so a display dense enough to want
 * more pixels than the widest copy still has somewhere to go — but only where no stored width
 * already claims that number. Two candidates with the same descriptor is a parse error, and the
 * ladder tops out at exactly the picture's own width whenever it is wide enough. A picture with no
 * stored widths gets no attribute at all: a one-entry `srcset` says the same thing `src` already
 * does.
 */
export function srcsetOf(picture?: Picture | null): string | undefined {
  if (!picture || picture.renditions.length === 0) return undefined
  const stored = picture.renditions.map(one => `${one.url} ${one.width}w`)
  const own = picture.width
  if (own && !picture.renditions.some(one => one.width === own)) stored.push(`${picture.url} ${own}w`)
  return stored.join(", ")
}

/**
 * The space a picture will take, for the browser to reserve before the bytes arrive.
 *
 * Given as the picture's own dimensions rather than the box it is drawn in: an `img` with a
 * width and a height has an aspect ratio, and the stylesheet decides the rest. A picture whose
 * size could not be read gets neither, and the page moves under it as it always did.
 */
export function sizeOf(picture?: Picture | null): {width?: number; height?: number} {
  if (!picture?.width || !picture.height) return {}
  return {width: picture.width, height: picture.height}
}

/**
 * How wide a picture is really painted, when it covers a box rather than fits inside one.
 *
 * `object-fit: cover` scales until both axes are covered and crops the overhang, so a box taller
 * than it is wide is filled by its height and the picture is drawn far wider than the box — and
 * the width the browser is promised has to say so, or a narrow slice fetches a copy several times
 * too small for the pixels it is stretched across. The box's own width where the proportions are
 * unknown, as for a picture the api could not measure.
 */
export function coveredWidth(box: {
  boxWidth: number
  boxHeight: number
  imageWidth?: number | null
  imageHeight?: number | null
}): number {
  const {boxWidth, boxHeight, imageWidth, imageHeight} = box
  if (!imageWidth || !imageHeight || boxHeight <= 0) return boxWidth
  return Math.max(boxWidth, Math.ceil((boxHeight * imageWidth) / imageHeight))
}
