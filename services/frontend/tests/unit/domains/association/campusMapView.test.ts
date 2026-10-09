import {describe, expect, it} from "vitest"
import {CAMPUS, HOME, MAX_ZOOM, MIN_ZOOM, panned, spotOf, zoomed, zoomedAt} from "@/domains/association/campusMap"

const PLATE = {width: 600, height: 450}

describe("the campus map", () => {
  it("puts the Lounge in the middle of the drawing", () => {
    expect(spotOf(CAMPUS.lounge)).toEqual({left: 50, top: 50})
  })

  it("puts the bus stop west and south of the Lounge, in metres across the drawn square", () => {
    const spot = spotOf(CAMPUS.busStop)

    expect(spot.left).toBeCloseTo(41.37, 1)
    expect(spot.top).toBeCloseTo(55.34, 1)
  })

  it("zooms about the middle, so what was centred stays centred", () => {
    const view = zoomed({...HOME, x: 40, y: -20}, 2)

    expect(view).toEqual({zoom: 2, x: 80, y: -40})
  })

  it("stops zooming at its nearest and its farthest", () => {
    expect(zoomed(HOME, 100).zoom).toBe(MAX_ZOOM)
    expect(zoomed(HOME, 0.01).zoom).toBe(MIN_ZOOM)
  })

  it("follows a drag", () => {
    expect(panned(HOME, {x: 30, y: -12}, PLATE)).toEqual({zoom: 1, x: 30, y: -12})
  })

  it("never drags the drawing off the plate", () => {
    const far = panned(HOME, {x: 5000, y: -5000}, PLATE)

    // The drawing is twice the plate's width across: half of it may leave on either side.
    expect(far.x).toBe(300)
    expect(far.y).toBe(-375)
  })

  it("zooms about the pointer, so what is under it stays under it", () => {
    // 100px right of the middle, the drawing's own middle being at the plate's: after doubling,
    // the drawing moves 100px left so the spot under the pointer is where it was.
    expect(zoomedAt(HOME, 2, {x: 100, y: 0}, PLATE)).toEqual({zoom: 2, x: -100, y: 0})
  })

  it("keeps the drawing over the whole plate when zooming out away from the middle", () => {
    const far = zoomedAt({zoom: 2, x: 900, y: 0}, 0.5, {x: 0, y: 0}, PLATE)

    expect(far).toEqual({zoom: 1, x: 300, y: 0})
  })
})
