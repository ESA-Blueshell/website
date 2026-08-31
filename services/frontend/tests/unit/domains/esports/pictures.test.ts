import {describe, expect, it} from "vitest"
import {coverWidth, sizeOf, srcsetOf} from "@/domains/esports/pictures"
import type {EsportsImage} from "@/domains/esports/adapters/esports"

/**
 * The one display string this domain composes from what the api answers.
 *
 * It lives here rather than in the payload because which widths a browser is offered is a
 * display decision, and the cases below are the ones that decide whether a browser reads the
 * attribute at all.
 */
const picture = (over: Partial<EsportsImage> = {}): EsportsImage => ({
  url: "/files/public/team-posters/abc.webp",
  path: "team-posters/abc.webp",
  width: 1000,
  height: 400,
  renditions: [
    {url: "/files/public/team-posters/abc-320.webp", width: 320},
    {url: "/files/public/team-posters/abc-640.webp", width: 640},
  ],
  ...over,
})

describe("srcsetOf", () => {
  it("offers every stored width, narrowest first, and the picture itself above them", () => {
    expect(srcsetOf(picture())).toBe(
      "/files/public/team-posters/abc-320.webp 320w, "
      + "/files/public/team-posters/abc-640.webp 640w, "
      + "/files/public/team-posters/abc.webp 1000w",
    )
  })

  /** Two candidates with the same descriptor is a parse error, and the ladder can top out. */
  it("leaves the picture itself out when a stored width already claims its number", () => {
    const at640 = picture({width: 640})

    expect(srcsetOf(at640)).toBe(
      "/files/public/team-posters/abc-320.webp 320w, /files/public/team-posters/abc-640.webp 640w",
    )
  })

  it("offers the stored widths alone when the picture's own size could not be read", () => {
    expect(srcsetOf(picture({width: null, height: null}))).toBe(
      "/files/public/team-posters/abc-320.webp 320w, /files/public/team-posters/abc-640.webp 640w",
    )
  })

  /** A one-entry srcset says exactly what `src` already said. */
  it("gives no attribute for a picture stored at one width, or for no picture at all", () => {
    expect(srcsetOf(picture({renditions: []}))).toBeUndefined()
    expect(srcsetOf(null)).toBeUndefined()
    expect(srcsetOf(undefined)).toBeUndefined()
  })
})

describe("sizeOf", () => {
  it("gives the picture's own dimensions, so an element has a ratio to reserve", () => {
    expect(sizeOf(picture())).toEqual({width: 1000, height: 400})
  })

  it("gives neither where the size could not be read, rather than guessing one", () => {
    expect(sizeOf(picture({width: null, height: null}))).toEqual({})
    expect(sizeOf(picture({height: null}))).toEqual({})
    expect(sizeOf(null)).toEqual({})
  })
})

/**
 * What a slice has to promise the browser, which is not the width of the slice.
 *
 * These are the cases that decide whether a banner is fetched blurry: a slice is a tall narrow
 * strip, and a picture set to cover one is drawn far wider than the strip is.
 */
describe("coverWidth", () => {
  it("is driven by the height where the box is narrower than the picture's shape", () => {
    // A 16x9 banner in a 200x352 slice is drawn 626 wide, not 200.
    expect(coverWidth({width: 200, height: 352}, {width: 1600, height: 900})).toBe(626)
  })

  it("is driven by the width where the box is wider than the picture's shape", () => {
    expect(coverWidth({width: 1200, height: 352}, {width: 1600, height: 900})).toBe(1200)
  })

  it("multiplies by the scale the picture is held at, so a held picture is not asked for short", () => {
    expect(coverWidth({width: 200, height: 352}, {width: 1600, height: 900}, 1.06)).toBe(664)
  })

  /** Every banner these pages store is 16x9, so that is the assumption when nothing is known. */
  it("assumes 16x9 for a picture whose own size was never read", () => {
    expect(coverWidth({width: 100, height: 900}, {})).toBe(1600)
    expect(coverWidth({width: 100, height: 900}, {width: null, height: null})).toBe(1600)
  })
})
