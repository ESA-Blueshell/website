import {ref} from "vue"
import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import TopBanner from "@/components/common/banners/TopBanner.vue"

const display = {
  lgAndUp: ref(true),
  md: ref(false),
  sm: ref(false),
  xs: ref(false),
}

vi.mock("vuetify", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuetify")>()
  return {
    ...(actual as Record<string, unknown>),
    useDisplay: () => display,
  }
})

describe("TopBanner", () => {
  it("renders uppercase title", () => {
    const wrapper = mount(TopBanner, {
      props: {title: "Events"},
    })

    expect(wrapper.text()).toContain("EVENTS")
  })
})
