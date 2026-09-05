import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {mountInApp, unmountAll} from "../helpers"
import Partners from "@/pages/partners/Partners.vue"

const {mockLoadNumbers, mockLoadEvents} = vi.hoisted(() => ({
  mockLoadNumbers: vi.fn(),
  mockLoadEvents: vi.fn(),
}))

vi.mock("@/domains/association/adapters/association", () => ({
  loadAssociationNumbers: mockLoadNumbers,
  loadEventsOnShow: mockLoadEvents,
  loadCurrentContributionPeriod: vi.fn(),
}))

const mounted: VueWrapper[] = []

const hold = (wrapper: VueWrapper<any>) => {
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => unmountAll(mounted, "Become a partner page"))

const mountPage = () => hold(mountInApp(Partners, {
  global: {
    stubs: {
      "router-link": {template: "<a><slot /></a>"},
      HeroBand: true,
      SliceBand: true,
      CallBand: true,
      BandRule: true,
    },
  },
}))

describe("Become a partner page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLoadNumbers.mockResolvedValue(null)
    mockLoadEvents.mockResolvedValue([])
  })

  it("offers every one of the seven things the association prints", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const offers = wrapper.get('[data-testid="partners-offers"]')
    expect(offers.findAll("li")).toHaveLength(7)
    const text = offers.text()
    for (const offer of [
      "Access to our members",
      "Representatives at our events",
      "Social media promotion",
      "Promotional material at our events",
      "Logos on our merch and posters",
      "Pages on our website",
      "Direct referrals of students",
    ]) {
      expect(text).toContain(offer)
    }
  })

  it("draws the reach as the six fields, adding up to the whole membership", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const chart = wrapper.get('[data-testid="partners-reach"]')
    expect(chart.text()).toContain("28.8%")
    expect(chart.text()).toContain("2.7%")
    expect(chart.findAll("li")).toHaveLength(6)
  })

  /**
   * Every placement is a picture of the thing itself, not a frame standing in for one.
   *
   * The words stay in the markup rather than inside the pictures, so they can be read out and
   * translated; the jersey is the exception, being artwork that already points at itself.
   */
  it("shows a partner where their name would go, on the things it goes on", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const places = wrapper.get('[data-testid="partners-places"]')
    expect(places.findAll("li")).toHaveLength(4)
    expect(places.text()).toContain("Your flyers here")
    expect(places.text()).toContain("Worn by the teams that play under our name")
    expect(places.findAll("img").length).toBeGreaterThanOrEqual(4)
  })

  it("shows who the association already works with", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="partners-wall"]').text()).toContain("In good company")
  })

  /** No prices anywhere: the page ends on a conversation, not on a package. */
  it("quotes no prices", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).not.toMatch(/€\s*\d/)
    expect(text.toLowerCase()).not.toContain("per year")
  })

  it("gives external affairs as the way to make contact, and no phone number", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const call = wrapper.findComponent({name: "CallBand"})
    const actions = call.props("actions") as {href: string}[]
    expect(actions[0].href).toBe("mailto:external-affairs@blueshell.utwente.nl")
    expect(wrapper.text()).not.toMatch(/\+31\s?6/)
  })
})
