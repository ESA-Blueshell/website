import {describe, expect, it} from "vitest"
import Partners from "@/pages/partners/Partners.vue"
import {mountInApp} from "../helpers"

describe("Partners page", () => {
  it("contains external affairs contact link", () => {
    const wrapper = mountInApp(Partners)

    expect(wrapper.get("a").attributes("href")).toBe("mailto:external-affairs@blueshell.utwente.nl")
  })
})
