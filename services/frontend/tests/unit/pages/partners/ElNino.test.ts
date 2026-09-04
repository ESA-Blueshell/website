import {describe, expect, it} from "vitest"
import ElNino from "@/pages/partners/ElNino.vue"
import {hrefs, mountInApp} from "../helpers"

describe("ElNino page", () => {
  it("contains all expected partner outbound links", () => {
    const wrapper = mountInApp(ElNino)
    const links = hrefs(wrapper)

    expect(links).toContain("https://www.elnino.tech/vacatures")
    expect(links).toContain("https://www.elnino.tech/getajob")
    expect(links).toContain("https://wa.me/31626978392")
  })
})
