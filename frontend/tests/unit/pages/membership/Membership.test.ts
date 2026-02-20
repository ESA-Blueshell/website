import {describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Membership from "@/pages/membership/Membership.vue"

const mockGoto = vi.hoisted(() => vi.fn())

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

describe("Membership page", () => {
  it("contains discord link and membership signup CTA", async () => {
    const wrapper = shallowMount(Membership, {
      global: {
        stubs: {
          ContributionPeriodComponent: true,
        },
      },
    })

    expect(wrapper.html()).toContain("https://discord.gg/23YMFQy")
    await wrapper.get("v-btn").trigger("click")
    expect(mockGoto).toHaveBeenCalledWith("membership/signup")
  })
})
