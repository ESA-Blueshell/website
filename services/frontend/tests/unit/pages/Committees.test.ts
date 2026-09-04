import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import Committees from "@/pages/Committees.vue"
import {settle} from "./helpers"

const {
  mockFindCommittees,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindCommittees: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findCommittees: mockFindCommittees,
}))

vi.mock("@/plugins/handleNetworkError.js", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("Committees page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("loads committees and renders cards", async () => {
    mockFindCommittees.mockResolvedValue({
      data: [
        {id: 1, name: "SiteCie", members: []},
      ],
    })

    const wrapper = mount(Committees, {
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

    expect(mockFindCommittees).toHaveBeenCalledTimes(1)
    expect(wrapper.find("[data-test='committee-card']").text()).toContain("SiteCie")
  })

  it("shows empty state when API returns no committees", async () => {
    mockFindCommittees.mockResolvedValue({data: []})

    const wrapper = mount(Committees)
    await settle()

    expect(wrapper.text()).toContain("No committees found")
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
  })

  it("falls back to empty state when committees request fails", async () => {
    const error = new Error("boom")
    mockFindCommittees.mockRejectedValue(error)

    const wrapper = mount(Committees)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
    expect(wrapper.text()).toContain("No committees found")
  })
})
