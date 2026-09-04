import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import Membership from "@/pages/membership/Membership.vue"

const mockGoto = vi.hoisted(() => vi.fn())

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

describe("Membership page", () => {
  it("contains discord link and membership signup CTA", async () => {
    const wrapper = mount(Membership, {
      global: {
        stubs: {
          ContributionPeriodComponent: true,
        },
      },
    })

    expect(wrapper.html()).toContain("https://discord.gg/23YMFQy")
    const cta = wrapper.findAll("button").find((button) => button.text() === "Become a member!")
    await cta?.trigger("click")
    expect(mockGoto).toHaveBeenCalledWith("membership/signup")
  })
})
