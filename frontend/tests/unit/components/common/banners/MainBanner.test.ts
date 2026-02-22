import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MainBanner from "@/components/common/banners/MainBanner.vue"

const {mockGoto} = vi.hoisted(() => ({
  mockGoto: vi.fn(),
}))

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

describe("MainBanner", () => {
  it("calls goto when join button is clicked", async () => {
    const wrapper = mount(MainBanner, {
      global: {
        mocks: {
          $vuetify: {
            display: {
              mdAndUp: true,
              smAndUp: true,
              smAndDown: false,
            },
          },
        },
      },
    })

    await wrapper.find("v-btn").trigger("click")
    expect(mockGoto).toHaveBeenCalledWith("membership/signup")
  })
})
