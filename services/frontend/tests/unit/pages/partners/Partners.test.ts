import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Partners from "@/pages/partners/Partners.vue"

describe("Partners page", () => {
  it("contains external affairs contact link", () => {
    const wrapper = shallowMount(Partners)

    expect(wrapper.get("a").attributes("href")).toBe("mailto:external-affairs@blueshell.utwente.nl")
  })
})
