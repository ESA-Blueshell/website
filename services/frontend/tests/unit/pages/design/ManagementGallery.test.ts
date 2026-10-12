import {afterEach, describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ManagementGallery from "@/pages/design/ManagementGallery.vue"
import {JobExecutionStatus} from "@/domains/jobs"

const gallery = () => mount(ManagementGallery, {attachTo: document.body})

describe("the management parts gallery", () => {
  afterEach(() => {
    document.body.innerHTML = ""
  })

  it("draws the parts in either half of the theme", async () => {
    const wrapper = gallery()

    expect(wrapper.find("[data-testid=gallery-search]").exists()).toBe(true)
    expect(wrapper.findAll(".state-mark")).toHaveLength(6)
    expect(wrapper.findAll(".role-mark")).toHaveLength(4)
    expect(wrapper.get("[data-theme]").attributes("data-theme")).toBe("dark")
    await wrapper.get("[data-testid=management-gallery-theme]").trigger("click")
    expect(wrapper.get("[data-theme]").attributes("data-theme")).toBe("light")
  })

  it("offers every job status the SDK names, and clears the search and the filter together", async () => {
    const wrapper = gallery()
    const search = wrapper.get<HTMLInputElement>("[data-testid=gallery-search]")
    const status = wrapper.get<HTMLInputElement>("[data-testid=gallery-status-search]")

    await status.trigger("click")
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    const rows = [...document.querySelectorAll<HTMLButtonElement>("[data-testid=gallery-status-list] [role=option]")]
    expect(rows.map((row) => row.textContent.trim())).toEqual(["Any", ...Object.values(JobExecutionStatus).map((value) => value.toLowerCase())])

    rows[1]!.click()
    await search.setValue("brevo")
    expect(status.element.value).toBe(rows[1]!.textContent.trim())
    await wrapper.get("[data-testid=gallery-filters-clear]").trigger("click")
    expect(status.element.value).toBe("Any")
    expect(search.element.value).toBe("")
    expect(wrapper.find("[data-testid=gallery-filters-clear]").exists()).toBe(false)
  })

  it("keeps the selection bar in view once cleared, and takes a tick again", async () => {
    const wrapper = gallery()
    const bar = wrapper.get("[data-testid=gallery-selection]")

    expect(bar.text()).toContain("2 selected")
    await wrapper.get("[data-testid=gallery-selection-clear]").trigger("click")
    expect(bar.text()).toContain("0 selected")
    await wrapper.get("[data-testid=gallery-tick]").trigger("click")
    expect(bar.text()).toContain("1 selected")
  })

  it("folds the fold-out and its note away", async () => {
    const wrapper = gallery()

    expect(wrapper.text()).toContain("Work that opens in place")
    await wrapper.get("[data-testid=gallery-fold-toggle]").trigger("click")
    expect(wrapper.text()).not.toContain("Work that opens in place")
  })
})
