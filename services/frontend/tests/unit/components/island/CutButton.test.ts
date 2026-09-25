import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import CutButton from "@/components/island/CutButton.vue"

const cut = (props: Record<string, unknown> = {}) =>
  mount(CutButton, {
    props,
    slots: {default: "Sign up"},
    global: {stubs: {RouterLink: RouterLinkStub}},
  })

describe("CutButton", () => {
  it("is a button where the page handles the press itself", () => {
    const wrapper = cut()

    expect(wrapper.element.tagName).toBe("BUTTON")
    expect(wrapper.attributes("type")).toBe("button")
    expect(wrapper.text()).toBe("Sign up")
  })

  it("sends the form it stands in when asked to", () => {
    expect(cut({submit: true}).attributes("type")).toBe("submit")
  })

  it("leaves a path to the router, and everything else to the browser", () => {
    expect(cut({href: "/membership"}).findComponent(RouterLinkStub).props("to")).toBe("/membership")
    expect(cut({href: "https://discord.gg/x"}).attributes("href")).toBe("https://discord.gg/x")
  })

  it("opens a new tab only for somewhere that is not the site", () => {
    const away = cut({href: "https://discord.gg/x", away: true})

    expect(away.attributes("target")).toBe("_blank")
    expect(away.attributes("rel")).toBe("noopener")
    expect(cut({href: "https://discord.gg/x"}).attributes("target")).toBeUndefined()
  })

  it("carries the tone it was given, and is plain without one", () => {
    expect(cut().classes()).toContain("island-cut--plain")
    expect(cut({tone: "solid"}).classes()).toContain("island-cut--solid")
    expect(cut({tone: "quiet"}).classes()).toContain("island-cut--quiet")
  })
})
