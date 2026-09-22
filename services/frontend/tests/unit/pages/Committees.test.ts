import {beforeEach, describe, expect, it, vi} from "vitest"
import Committees from "@/pages/Committees.vue"
import {mountInApp, settle} from "./helpers"

const {
  mockListCommittees,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockListCommittees: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/committees", () => ({
  listCommittees: mockListCommittees,
}))

vi.mock("@/plugins/handleNetworkError.js", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("Committees page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("loads committees and renders cards", async () => {
    mockListCommittees.mockResolvedValue([
      {id: 1, name: "SiteCie", members: []},
      // A committee the api answered without a number is keyed by its name instead.
      {name: "EventCie", members: []},
    ])

    const wrapper = mountInApp(Committees, {
      global: {
        stubs: {
          CommitteeCard: {
            props: ["committee"],
            template: "<div data-test='committee-card'>{{ committee.name }}</div>",
          },
        },
      },
    })

    await settle()

    expect(mockListCommittees).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll("[data-test='committee-card']").map(one => one.text()))
      .toEqual(["SiteCie", "EventCie"])
  })

  it("shows empty state when API returns no committees", async () => {
    mockListCommittees.mockResolvedValue([])

    const wrapper = mountInApp(Committees)
    await settle()

    expect(wrapper.text()).toContain("No committees found")
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
  })

  it("falls back to empty state when committees request fails", async () => {
    const error = new Error("boom")
    mockListCommittees.mockRejectedValue(error)

    const wrapper = mountInApp(Committees)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
    expect(wrapper.text()).toContain("No committees found")
  })
})
