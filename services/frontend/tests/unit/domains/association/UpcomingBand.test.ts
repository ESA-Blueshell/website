import {beforeEach, describe, expect, it, vi} from "vitest"
import {DateTime} from "luxon"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import UpcomingBand from "@/domains/association/island/UpcomingBand.vue"
import {stateOf} from "@/domains/association/island/useUpcomingEvents"

const {mockLoad} = vi.hoisted(() => ({mockLoad: vi.fn()}))

vi.mock("@/domains/association/adapters/association", () => ({loadUpcomingEvents: mockLoad}))

const coming = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  title: `Event ${id}`,
  startTime: "2026-10-03T19:00:00Z",
  endTime: "2026-10-03T23:00:00Z",
  location: "Esports Lounge Twente",
  description: "What  the art\ncannot say.",
  membersOnly: false,
  signUp: true,
  signUpCount: 6,
  signUpLimit: 24,
  banner: {url: `/art/${id}.webp`, path: `art/${id}.webp`, width: 1080, height: 1080, renditions: []},
  ...over,
})

const mountBand = () => mount(UpcomingBand, {
  global: {stubs: {RouterLink: RouterLinkStub, PosterStrip: true}},
})

describe("UpcomingBand", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("draws nothing while nothing is coming", async () => {
    mockLoad.mockResolvedValue({events: [], total: 0})
    const wrapper = mountBand()
    await flushPromises()

    expect(wrapper.find("[data-testid=home-upcoming]").exists()).toBe(false)
  })

  it("counts every event coming, and leads to all of them", async () => {
    mockLoad.mockResolvedValue({events: [coming(1), coming(2, {banner: undefined})], total: 11})
    const wrapper = mountBand()
    await flushPromises()

    expect(wrapper.find("[data-testid=home-upcoming-head-count]").text()).toContain("11")
    expect(wrapper.findComponent(RouterLinkStub).props("to")).toBe("/events")
  })

  it("hands the strip each event as a poster, pinned dark, leading to its own page", async () => {
    mockLoad.mockResolvedValue({events: [coming(1), coming(2, {banner: undefined})], total: 2})
    const wrapper = mountBand()
    await flushPromises()

    const strip = wrapper.findComponent({name: "PosterStrip"})
    expect(strip.classes()).toContain("island-dark")
    const [art, plate] = strip.props("items")
    expect(art).toMatchObject({id: 1, banner: "/art/1.webp", href: "/events/1", said: "What the art cannot say."})
    expect(art.state).toBe("Sign-ups open · 18 of 24 places")
    expect(plate.banner).toBeUndefined()
    expect(plate.where).toBe("Esports Lounge Twente")
  })

  it("asks for the next page when the strip nears its end, and stops once a page comes back short", async () => {
    mockLoad.mockResolvedValueOnce({events: Array.from({length: 8}, (_, at) => coming(at + 1)), total: 9})
    mockLoad.mockResolvedValueOnce({events: [coming(8), coming(9)], total: 9})
    const wrapper = mountBand()
    await flushPromises()

    wrapper.findComponent({name: "PosterStrip"}).vm.$emit("needs-more")
    await flushPromises()
    wrapper.findComponent({name: "PosterStrip"}).vm.$emit("needs-more")
    await flushPromises()

    expect(mockLoad).toHaveBeenCalledTimes(2)
    expect(mockLoad).toHaveBeenLastCalledWith(8, 1)
    expect(wrapper.findComponent({name: "PosterStrip"}).props("items")).toHaveLength(9)
  })

  it("asks once while a read is in flight, and draws an event nobody described by its title", async () => {
    let release: (page: unknown) => void = () => undefined
    mockLoad.mockResolvedValueOnce({events: Array.from({length: 8}, (_, at) => coming(at + 1)), total: 12})
    mockLoad.mockImplementationOnce(() => new Promise(resolve => {
      release = resolve
    }))
    const wrapper = mountBand()
    await flushPromises()

    const strip = () => wrapper.findComponent({name: "PosterStrip"})
    strip().vm.$emit("needs-more")
    strip().vm.$emit("needs-more")
    release({events: [coming(9, {
      description: undefined,
      banner: {url: "/art/9.webp", path: "art/9.webp", width: null, height: null, renditions: []},
    })], total: 12})
    await flushPromises()

    expect(mockLoad).toHaveBeenCalledTimes(2)
    expect(strip().props("items").at(-1)).toMatchObject({said: "", width: undefined, height: undefined})
  })

  it("stops asking after a read that fails", async () => {
    mockLoad.mockRejectedValue(new Error("offline"))
    const wrapper = mountBand()
    await flushPromises()

    expect(wrapper.find("[data-testid=home-upcoming]").exists()).toBe(false)
  })
})

describe("what a poster says about signing up", () => {
  const now = DateTime.fromISO("2026-09-21T12:00:00Z")
  const event = (over: Record<string, unknown>) => ({...coming(1), ...over}) as never

  it("says there is nothing to sign up for", () => {
    expect(stateOf(event({signUp: false}), now)).toBe("No sign-ups, just walk in")
  })

  it("says sign-ups closed once the deadline passed", () => {
    expect(stateOf(event({signUpDeadline: "2026-09-20T12:00:00Z"}), now)).toBe("Sign-ups closed")
  })

  it("counts the places left under a limit, and says when there are none", () => {
    expect(stateOf(event({signUpDeadline: "2026-10-01T12:00:00Z"}), now)).toBe("Sign-ups open · 18 of 24 places")
    expect(stateOf(event({signUpCount: 30}), now)).toBe("Sign-ups full")
  })

  it("counts heads where there is no limit, and names a members-only event", () => {
    expect(stateOf(event({signUpLimit: undefined, signUpCount: 8, membersOnly: true}), now))
      .toBe("Sign-ups open · members only · 8 going")
  })

  it("reads the clock itself when none is given", () => {
    expect(stateOf(event({signUp: false}))).toBe("No sign-ups, just walk in")
  })
})
