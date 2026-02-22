import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import CreateAccount from "@/pages/login/CreateAccount.vue"

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<button data-test='submit' @click=\"$emit('submitted', true)\">submit</button>",
  },
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {
    name: "TopBanner",
    template: "<div />",
  },
}))

describe("CreateAccount page", () => {
  it("switches to success state when user form emits submitted=true", async () => {
    const wrapper = mount(CreateAccount, {
      global: {
        stubs: {
          TopBanner: true,
        },
      },
    })

    await wrapper.get("[data-test='submit']").trigger("click")
    expect(wrapper.text()).toContain("Your account has successfully been created")
  })
})
