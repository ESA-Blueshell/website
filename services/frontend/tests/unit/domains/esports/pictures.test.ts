import {describe, expect, it} from "vitest"
import {sizeOf, srcsetOf} from "@/domains/esports/pictures"
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
