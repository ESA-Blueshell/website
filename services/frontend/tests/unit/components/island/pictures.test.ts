import {describe, expect, it} from "vitest"
import {backgroundOf, coveredWidth, sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"

/**
 * The display strings the island composes from what the api answers.
 *
 * They live here rather than in the payload because which widths a browser is offered is a
 * display decision, and the cases below are the ones that decide whether a browser reads the
 * attribute at all.
 */
const picture = (over: Partial<Picture> = {}): Picture => ({
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

describe("coveredWidth", () => {
  it("gives the box's own width where the box is wider than the picture needs", () => {
    // 16:9 covering a 1200x300 box: the width fills first, so nothing is added.
    expect(coveredWidth({boxWidth: 1200, boxHeight: 300, imageWidth: 1920, imageHeight: 1080}))
      .toBe(1200)
  })

  it("gives the width the height demands where the box is tall and narrow", () => {
    // The complaint itself: a slice of a band, 123 across and 352 tall, drawn at 626.
    expect(coveredWidth({boxWidth: 123, boxHeight: 352, imageWidth: 1920, imageHeight: 1080}))
      .toBe(626)
  })

  it("scales with the picture's proportions rather than assuming a shape", () => {
    // The same box, and a wide picture needs three times the width a tall one does.
    expect(coveredWidth({boxWidth: 100, boxHeight: 300, imageWidth: 300, imageHeight: 100}))
      .toBe(900)
    expect(coveredWidth({boxWidth: 100, boxHeight: 300, imageWidth: 100, imageHeight: 300}))
      .toBe(100)
  })

  it("falls back to the box where the picture's proportions are not known", () => {
    expect(coveredWidth({boxWidth: 123, boxHeight: 352})).toBe(123)
    expect(coveredWidth({boxWidth: 123, boxHeight: 352, imageWidth: 1920, imageHeight: null}))
      .toBe(123)
    expect(coveredWidth({boxWidth: 123, boxHeight: 352, imageWidth: 0, imageHeight: 0})).toBe(123)
  })

  it("falls back to the box where nothing has been laid out yet", () => {
    expect(coveredWidth({boxWidth: 123, boxHeight: 0, imageWidth: 1920, imageHeight: 1080}))
      .toBe(123)
  })
})

describe("backgroundOf", () => {
  const picture = {
    url: "/art/full.webp",
    path: "art/full.webp",
    width: 2560,
    height: 1440,
    renditions: [
      {url: "/art/320.webp", width: 320},
      {url: "/art/640.webp", width: 640},
      {url: "/art/1280.webp", width: 1280},
    ],
  }

  it("offers the width it is drawn at and twice that for a dense display", () => {
    expect(backgroundOf(picture, 640)).toBe(
      "image-set(url('/art/640.webp') 1x, url('/art/1280.webp') 2x)",
    )
  })

  it("picks the nearest stored width rather than an exact one", () => {
    expect(backgroundOf(picture, 300)).toBe(
      "image-set(url('/art/320.webp') 1x, url('/art/640.webp') 2x)",
    )
  })

  it("gives a picture with one stored width plainly", () => {
    const single = {...picture, renditions: [{url: "/art/640.webp", width: 640}]}
    expect(backgroundOf(single, 640)).toBe("url('/art/640.webp')")
  })

  it("falls back to the full-size picture when none is stored", () => {
    expect(backgroundOf({...picture, renditions: []}, 640)).toBe("url('/art/full.webp')")
  })

  it("has nothing to give for no picture", () => {
    expect(backgroundOf(null, 640)).toBeUndefined()
    expect(backgroundOf(undefined, 640)).toBeUndefined()
  })
})
