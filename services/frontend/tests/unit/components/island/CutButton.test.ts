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

  it("hands a file to the browser to save, even when the file is on the site", () => {
    const wrapper = cut({href: "/assets/statutes.pdf", download: "Statutes.pdf"})

    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    expect(wrapper.element.tagName).toBe("A")
    expect(wrapper.attributes("href")).toBe("/assets/statutes.pdf")
    expect(wrapper.attributes("download")).toBe("Statutes.pdf")
    expect(cut({href: "https://example.org/a"}).attributes("download")).toBeUndefined()
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

  it("draws a smaller button for a bar of several", () => {
    expect(cut({small: true}).classes()).toContain("island-cut--small")
    expect(cut().classes()).not.toContain("island-cut--small")
  })
})
