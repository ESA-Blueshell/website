import {describe, expect, it} from "vitest"
import {eventSlices, FEWEST, SAMPLED} from "@/domains/association/events"
import type {AssociationEvent} from "@/domains/association/adapters/association"

const art = (name: string) => ({
  path: `event-banners/${name}.webp`,
  url: `/files/public/event-banners/${name}.webp`,
  width: 1280,
  height: 720,
  renditions: [320, 640, 1280].map(width => ({
    url: `/files/public/event-banners/${name}-${width}.webp`,
    width,
  })),
})

const event = (id: number, over: Partial<AssociationEvent> = {}): AssociationEvent => ({
  id,
  title: `Event ${id}`,
  approved: true,
  membersOnly: false,
  signUp: false,
  signUpCount: 0,
  location: "The Bastille",
  startTime: "2026-03-12T19:00:00.000Z",
  endTime: "2026-03-12T23:00:00.000Z",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:00.000Z",
  version: 0,
  banner: {
    eventId: id,
    fileId: id,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    version: 0,
    image: art(`event-${id}`),
  },
  ...over,
})

const many = (count: number) => Array.from({length: count}, (_, index) => event(index + 1))

describe("eventSlices", () => {
  it("draws at most the six the band is designed for", () => {
    expect(eventSlices(many(9))).toHaveLength(SAMPLED)
  })

  it("keeps the order the api answered in, which is newest first", () => {
    expect(eventSlices(many(6)).map(slice => slice.id)).toEqual([1, 2, 3, 4, 5, 6])
  })

  // The whole band or none of it: a short row and a tile with a hole in it are the two
  // defects the floor exists to prevent.
  it("draws nothing at all where too few events qualify", () => {
    expect(eventSlices(many(FEWEST - 1))).toEqual([])
    expect(eventSlices([])).toEqual([])
    expect(eventSlices(null)).toEqual([])
  })

  it("draws the band the moment enough events qualify", () => {
    expect(eventSlices(many(FEWEST))).toHaveLength(FEWEST)
  })

  // An event the api counts as having a banner whose file it cannot serve would be a slice
  // with a hole in it, so it is not one of the events that qualify.
  it("drops an event whose art cannot be served", () => {
    const broken = [...many(5), event(6, {banner: {
      eventId: 6, fileId: 6, version: 0,
      createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z",
      image: null,
    }})]

    expect(eventSlices(broken).map(slice => slice.id)).toEqual([1, 2, 3, 4, 5])
  })

  it("takes the whole band away where the art failing leaves too few", () => {
    const broken = many(5).map(one => ({...one, banner: null}))

    expect(eventSlices([...broken, event(6), event(7), event(8)])).toEqual([])
  })

  it("hands the band the rendition ladder and the picture's own size", () => {
    const [slice] = eventSlices(many(6))

    expect(slice!.srcset).toContain("/files/public/event-banners/event-1-320.webp 320w")
    expect(slice!.banner).toBe("/files/public/event-banners/event-1-1280.webp")
    expect(slice).toMatchObject({width: 1280, height: 720})
  })

  it("says on the slice that an event was members-only", () => {
    const [members, open] = eventSlices([
      event(1, {membersOnly: true}), event(2), event(3), event(4),
    ])

    expect(members!.meta).toContain("Members only")
    expect(open!.meta).not.toContain("Members only")
  })

  it("opens only onto an event that recorded where it happened", () => {
    const [known, unknown] = eventSlices([
      event(1), event(2, {location: null}), event(3), event(4),
    ])

    expect(known!.expandable).toBe(true)
    expect(unknown!.expandable).toBe(false)
  })
})
