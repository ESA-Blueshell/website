import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import EditPage from "@/components/island/EditPage.vue"

const mountPage = (slots: Record<string, string> = {}) => mount(EditPage, {
  props: {testid: "thing-edit", eyebrow: "Competition", title: "Edit season", back: {to: "/competition?season=3", label: "Competition"}, accent: "#ff4655"},
  slots: {default: "<form data-testid=form />", ...slots},
  global: {stubs: {RouterLink: RouterLinkStub, VMain: {template: "<main><slot /></main>"}}},
})

describe("an edit page", () => {
  it("heads the page with the way back, what is edited and its own actions, in the thing's colour", () => {
    const wrapper = mountPage({actions: "<a data-testid=see>See it</a>"})

    expect(wrapper.getComponent(RouterLinkStub).props("to")).toBe("/competition?season=3")
    expect(wrapper.get("h1").text()).toBe("Edit season")
    expect(wrapper.text()).toContain("Competition")
    expect(wrapper.find("[data-testid=see]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=thing-edit]").attributes("style")).toContain("--edit-accent: #ff4655")
  })

  it("puts the form beside a preview that folds away, and the save bar under the form", async () => {
    const wrapper = mountPage({preview: "<div data-testid=drawn />", footer: "<button data-testid=save />"})
    const toggle = wrapper.get(".edit-page__preview-toggle")

    expect(wrapper.find("[data-testid=thing-edit-preview] [data-testid=drawn]").exists()).toBe(true)
    expect(wrapper.find(".edit-page__save [data-testid=save]").exists()).toBe(true)
    await toggle.trigger("click")
    expect(wrapper.get("[data-testid=thing-edit-preview]").classes()).toContain("edit-page__preview--shut")
    expect(toggle.attributes("aria-expanded")).toBe("false")
    await toggle.trigger("click")
    expect(toggle.attributes("aria-expanded")).toBe("true")
  })

  it("draws no preview, actions or save bar it is not given", () => {
    const wrapper = mountPage()

    expect(wrapper.find("[data-testid=thing-edit-preview]").exists()).toBe(false)
    expect(wrapper.find(".edit-page__actions").exists()).toBe(false)
    expect(wrapper.find(".edit-page__save").exists()).toBe(false)
  })
})
