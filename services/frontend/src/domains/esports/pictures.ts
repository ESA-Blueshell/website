import type {EsportsImage} from "./adapters/esports"

/**
 * How a picture is handed to the markup that draws it.
 *
 * The api answers with widths and urls; the string a browser reads is composed here, because
 * which widths a browser is offered is a display decision and belongs where the markup is
 * written. The companion decision — `sizes`, how wide the picture will actually be drawn — is
 * layout knowledge and stays in the component that owns the layout.
 */

/**
 * The `srcset` for a picture, or nothing where there is only one of it.
 *
 * The full-size picture is offered alongside the stored widths, so a display dense enough to
 * want more pixels than the widest copy still has somewhere to go — but only where no stored
 * width already claims that number. Two candidates with the same descriptor is a parse error,
 * and the ladder tops out at exactly the picture's own width whenever it is wide enough.
 *
 * A picture with no stored widths gets no attribute at all: a one-entry `srcset` says the same
 * thing `src` already does.
 */
export function srcsetOf(picture?: EsportsImage | null): string | undefined {
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
export function sizeOf(picture?: EsportsImage | null): {width?: number; height?: number} {
  if (!picture?.width || !picture.height) return {}
  return {width: picture.width, height: picture.height}
}

/**
 * How wide a picture is really painted, when it covers a box rather than fits inside one.
 *
 * `object-fit: cover` scales a picture until it covers both axes and crops what hangs over, so
 * a box taller than it is wide is filled by the height: the picture ends up drawn far wider
 * than the box, and the width a browser is promised has to say so. A narrow slice of a band is
 * exactly that shape, and asking for its own width alone fetched a copy several times too
 * small for the pixels it was stretched across.
 *
 * The box's own width where the picture's proportions are not known, which is what a picture
 * whose size the api could not read has to fall back to.
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
