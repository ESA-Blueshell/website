import {describe, expect, it, vi} from "vitest"
import Contact from "@/pages/Contact.vue"
import {hrefs, mountInApp} from "./helpers"

const mockGoto = vi.hoisted(() => vi.fn())

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

describe("Contact page", () => {
  it("contains contact links and routes to membership via goto", async () => {
    const wrapper = mountInApp(Contact)

    await wrapper.get("span.text-decoration-underline").trigger("click")
    expect(mockGoto).toHaveBeenCalledWith("membership")

    const allHrefs = hrefs(wrapper)
    expect(allHrefs).toContain("mailto:board@blueshell.utwente.nl")
    expect(allHrefs).toContain("http://localhost:3000/api/discord/invite/welcome")
    expect(wrapper.get("iframe").attributes("src")).toContain("google.com/maps/embed")
  })
})
