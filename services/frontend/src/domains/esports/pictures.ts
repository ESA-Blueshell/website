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
 * How wide a picture is really drawn, in css pixels, inside a box it is set to cover.
 *
 * `object-fit: cover` scales a picture until it fills the box on both axes, so a wide picture
 * in a tall narrow box is drawn far wider than the box is: a 16x9 banner in a slice 200 wide
 * and 352 tall is drawn at 626, and asking the browser for 200 gets a picture blurred to two
 * and a half times its size. Which is what `sizes` has to be told, since `sizes` is a promise
 * about the drawn width and not about the element's.
 *
 * [zoom] is any scale the picture is held at on top of that. A picture whose own dimensions
 * are unknown is assumed to be 16x9, which every banner on these pages is stored as.
 */
export function coverWidth(
  box: {width: number; height: number},
  picture: {width?: number | null; height?: number | null},
  zoom = 1,
): number {
  const aspect = picture.width && picture.height ? picture.width / picture.height : 16 / 9
  return Math.ceil(Math.max(box.width, box.height * aspect) * zoom)
}
