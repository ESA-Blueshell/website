import {describe, expect, it} from "vitest"
import {RouterLinkStub} from "@vue/test-utils"
import Contact from "@/pages/Contact.vue"
import {hrefs, mountInApp} from "./helpers"

describe("Contact page", () => {
  it("contains contact links and links to the membership page", () => {
    const wrapper = mountInApp(Contact, {global: {stubs: {RouterLink: RouterLinkStub}}})

    const join = wrapper.getComponent(RouterLinkStub)
    expect(join.text()).toBe("Join us!")
    expect(join.props("to")).toBe("/membership")

    const allHrefs = hrefs(wrapper)
    expect(allHrefs).toContain("mailto:board@blueshell.utwente.nl")
    expect(allHrefs).toContain("http://localhost:3000/api/discord/invite/welcome")
    expect(wrapper.get("iframe").attributes("src")).toContain("google.com/maps/embed")
  })
})
