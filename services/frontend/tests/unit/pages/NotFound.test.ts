import {describe, expect, it} from "vitest"
import NotFound from "@/pages/NotFound.vue"
import {mountInApp} from "./helpers"

describe("NotFound page", () => {
  it("renders fallback text and a return link", () => {
    const wrapper = mountInApp(NotFound)

    expect(wrapper.text()).toContain("Uh oh, we made a fucky wucky")
    expect(wrapper.get("a").attributes("href")).toBe("/")
  })
})
