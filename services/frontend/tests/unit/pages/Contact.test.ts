import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import Contact from "@/pages/Contact.vue"
import {hrefs, settle} from "./helpers"

const mountPage = () => mount(Contact, {
  attachTo: document.body,
  global: {stubs: {RouterLink: {props: ["to"], template: "<a :href=\"to\"><slot /></a>"}}},
})

function withClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, "clipboard", {configurable: true, value: {writeText}})
}

describe("Contact page", () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, "clipboard")
    vi.unstubAllGlobals()
    document.body.replaceChildren()
  })

  it("stands on the island, with no Vuetify and nothing of Google's", () => {
    const wrapper = mountPage()

    expect(wrapper.find("[data-testid=contact-island]").exists()).toBe(true)
    expect(wrapper.find("[class^=v-]").exists()).toBe(false)
    expect(wrapper.get("h1").text()).toBe("Ask the board")
    expect(wrapper.find("iframe").exists()).toBe(false)
    expect(wrapper.html()).not.toMatch(/google/i)
  })

  it("reaches the board by mail, in #board-questions and at the Lounge", () => {
    const wrapper = mountPage()

    expect(wrapper.findAll("[data-testid^=contact-way-]")).toHaveLength(3)
    expect(wrapper.get("[data-testid=contact-way-mail] a").attributes("href")).toBe("mailto:board@blueshell.utwente.nl")
    expect(wrapper.get("[data-testid=contact-board-questions]").text()).toBe("#board-questions")
    expect(wrapper.get("[data-testid=contact-board-questions]").attributes("href"))
      .toBe("http://localhost:3000/api/discord/invite/board")
    expect(wrapper.get("[data-testid=contact-way-discord]").text()).toContain("Ask your questions on Discord.")
    expect(hrefs(wrapper)).toContain("/membership")
  })

  it("shows the Lounge on openstreetmap.org, in a new tab", () => {
    const wrapper = mountPage()
    const link = wrapper.get("[data-testid=contact-lounge-map]")

    expect(link.attributes("href")).toMatch(/^https:\/\/www\.openstreetmap\.org\/\?mlat=52\.243214&mlon=6\.851979/)
    expect(link.attributes("target")).toBe("_blank")
  })

  it("shows the Lounge in the maps app on a touch screen", () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query === "(pointer: coarse)", addEventListener: () => {}, removeEventListener: () => {},
    }))
    const wrapper = mountPage()
    const link = wrapper.get("[data-testid=contact-lounge-map]")

    expect(link.attributes("href")).toMatch(/^geo:52\.243214,6\.851979/)
    expect(link.attributes("target")).toBeUndefined()
  })

  it("copies the post address, and says so", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    withClipboard(writeText)
    const wrapper = mountPage()

    await wrapper.get("[data-testid=contact-copy-address]").trigger("click")
    await settle()

    expect(writeText).toHaveBeenCalledWith("Blueshell Esports\nPostbus 217 (Bastille 49)\n7500 AE Enschede\nNetherlands")
    expect(wrapper.get("[data-testid=contact-copy-address]").text()).toBe("Copied")
  })

  it("says to select the address where the browser will not copy it", async () => {
    withClipboard(vi.fn().mockRejectedValue(new Error("denied")))
    const wrapper = mountPage()

    await wrapper.get("[data-testid=contact-copy-address]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=contact-copy-address]").text()).toBe("Select it to copy")
  })

  it("says the same where the browser has no clipboard at all", async () => {
    const wrapper = mountPage()

    await wrapper.get("[data-testid=contact-copy-address]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=contact-copy-address]").text()).toBe("Select it to copy")
  })
})
