import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CutRow from "@/components/island/CutRow.vue"

describe("a cut row", () => {
  it("leads somewhere when it has a page to open, with what it says at its end", () => {
    const wrapper = mount(CutRow, {
      props: {title: "Password", meta: "Every other sign-in ends", to: "/account/security/password", testid: "row"},
      slots: {glyph: "<svg data-testid='glyph' />", end: "<span>On</span>"},
    })

    const row = wrapper.get("[data-testid=row]")
    expect(row.attributes("to")).toBe("/account/security/password")
    expect(row.text()).toContain("Password")
    expect(row.text()).toContain("Every other sign-in ends")
    expect(row.find("[data-testid=glyph]").exists()).toBe(true)
    expect(row.find(".cut-row__end").text()).toBe("On")
  })

  it("holds its own action where it leads nowhere", () => {
    const wrapper = mount(CutRow, {
      props: {title: "Firefox on Linux", testid: "row"},
      slots: {tag: "<span data-testid='tag'>This browser</span>", end: "<button>Sign out</button>"},
    })

    const row = wrapper.get("[data-testid=row]")
    expect(row.element.tagName).toBe("DIV")
    expect(row.find(".cut-row__meta").exists()).toBe(false)
    expect(row.find(".cut-row__title [data-testid=tag]").exists()).toBe(true)
    expect(row.find("button").text()).toBe("Sign out")
  })
})
