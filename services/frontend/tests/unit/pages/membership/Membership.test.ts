import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import Membership from "@/pages/membership/Membership.vue"
import {FLOORS, MEMBERS_CLAIMED} from "@/domains/association/numbers"
import {mountInApp} from "../helpers"

const counted = {
  boards: 9,
  committees: 15,
  eventsLastYear: 63,
  gamesPlayed: 5,
  seasonsPlayed: 12,
  teamsThisSeason: 13,
}

const period = {
  id: 1,
  startDate: "2025-09-01",
  endDate: "2026-08-31",
  halfYearCutoffDate: "2026-02-01",
  fullYearFee: 20,
  halfYearFee: 12.5,
  alumniFee: 10,
  createdAt: "2025-09-01T00:00:00.000Z",
  updatedAt: "2025-09-01T00:00:00.000Z",
  version: 1,
}

/** Recent events with art, as the api answers with them: enough of them to make a band. */
const withArt = (nth: number) => ({
  id: 550 + nth,
  title: `Sampled Event ${nth}`,
  location: "Predator Esports Lounge",
  startTime: `2026-0${nth}-0${nth}T19:00:00.000Z`,
  endTime: `2026-0${nth}-0${nth}T23:00:00.000Z`,
  approved: true,
  membersOnly: false,
  signUp: false,
  signUpCount: 0,
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:00.000Z",
  version: 0,
  banner: {
    eventId: 550 + nth, fileId: 550 + nth, version: 0,
    createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z",
    image: {
      path: `event-banners/sampled-${nth}.webp`,
      url: `/files/public/event-banners/sampled-${nth}.webp`,
      width: 1280,
      height: 720,
      renditions: [320, 1280].map(width => ({
        url: `/files/public/event-banners/sampled-${nth}-${width}.webp`, width,
      })),
    },
  },
})

const sixWithArt = [1, 2, 3, 4, 5, 6].map(withArt)

const loadAssociationNumbers = vi.fn(async () => counted as typeof counted | null)
const loadRecentEventsWithArt = vi.fn(async () => sixWithArt as typeof sixWithArt | null)
const loadCurrentContributionPeriod = vi.fn(async () => period as typeof period | null)

vi.mock("@/domains/association/adapters/association", () => ({
  loadAssociationNumbers: () => loadAssociationNumbers(),
  loadCurrentContributionPeriod: () => loadCurrentContributionPeriod(),
  loadRecentEventsWithArt: () => loadRecentEventsWithArt(),
}))

const mountPage = () =>
  mountInApp(Membership, {
    global: {
      stubs: {
        RouterLink: {props: ["to"], template: "<a :data-to='to'><slot /></a>"},
        Motion: {template: "<div><slot /></div>"},
      },
    },
  })

const figure = (wrapper: ReturnType<typeof mountPage>, id: string) =>
  wrapper.find(`[data-testid="membership-numbers-${id}-value"]`).text()

const targets = (wrapper: ReturnType<typeof mountPage>) =>
  wrapper.findAll("a[data-to]").map(node => node.attributes("data-to"))

describe("Membership page", () => {
  beforeEach(() => {
    loadAssociationNumbers.mockResolvedValue(counted)
    loadCurrentContributionPeriod.mockResolvedValue(period)
    loadRecentEventsWithArt.mockResolvedValue(sixWithArt)
  })

  it("stands on the island", () => {
    expect(mountPage().find('[data-testid="membership-island"]').exists()).toBe(true)
  })

  // The band is what a visitor lands on, so it may never be empty, blank or pulsing.
  it("shows the published floors before the numbers arrive", () => {
    const wrapper = mountPage()

    expect(figure(wrapper, "members")).toBe(`${MEMBERS_CLAIMED}+`)
    expect(figure(wrapper, "teams")).toBe(`${FLOORS.teamsThisSeason}+`)
    expect(figure(wrapper, "committees")).toBe(`${FLOORS.committees}+`)
    expect(figure(wrapper, "events")).toBe(`${FLOORS.eventsLastYear}+`)
  })

  it("replaces the floors with the counted numbers where they land", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(figure(wrapper, "teams")).toBe("13")
    expect(figure(wrapper, "committees")).toBe("15")
    expect(figure(wrapper, "events")).toBe("63")
  })

  // The member count is permission-gated and unreadable while logged out, so it stays a claim.
  it("keeps the member count a claim after the numbers land", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(figure(wrapper, "members")).toBe(`${MEMBERS_CLAIMED}+`)
  })

  it("keeps the floors standing when the read fails", async () => {
    loadAssociationNumbers.mockResolvedValue(null)
    const wrapper = mountPage()
    await flushPromises()

    expect(figure(wrapper, "teams")).toBe(`${FLOORS.teamsThisSeason}+`)
    expect(figure(wrapper, "events")).toBe(`${FLOORS.eventsLastYear}+`)
  })

  it("quotes this year's fees and the note about them changing", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-fees-full-year-amount"]').text()).toContain("20,00")
    expect(wrapper.find('[data-testid="membership-fees-half-year-amount"]').text()).toContain("12,50")
    expect(wrapper.find('[data-testid="membership-fees-alumni-amount"]').text()).toContain("10,00")
    expect(wrapper.find('[data-testid="membership-fees-note"]').text()).toContain("subject to change")
  })

  it("says the fees are not listed rather than inventing one", async () => {
    loadCurrentContributionPeriod.mockResolvedValue(null)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-fees-unlisted"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="membership-fees-full-year-amount"]').exists()).toBe(false)
  })

  it("leads to the signup step from the hero and from the last band", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-hero-join"]').attributes("data-to"))
      .toBe("/membership/signup")
    expect(wrapper.find('[data-testid="membership-join-signup"]').attributes("data-to"))
      .toBe("/membership/signup")
    expect(targets(wrapper)).toContain("/membership/signup")
  })

  it("says what membership gets somebody, in the association's own claims", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-perks-discord"]').exists()).toBe(true)
    // Two different competitions, and the association has fielded teams in both.
    const esports = wrapper.find('[data-testid="membership-perks-esports"]').text()
    expect(esports).toContain("Dutch College Esports Series")
    expect(esports).toContain("Dutch Student League")
  })

  it("shows the recent events the association ran, with their real titles", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-events"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="membership-events-551"]').text())
      .toContain("Sampled Event 1")
  })

  // The whole band or none of it, so the page never carries a short row of slices.
  it("takes the whole band away where too few events qualify", async () => {
    loadRecentEventsWithArt.mockResolvedValue(sixWithArt.slice(0, 3))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-events"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="membership-perks"]').exists()).toBe(true)
  })

  it("takes the band away where the api would not say", async () => {
    loadRecentEventsWithArt.mockResolvedValue(null)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="membership-events"]').exists()).toBe(false)
  })
})
