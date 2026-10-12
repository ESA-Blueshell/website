import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SearchPicker from "@/components/island/SearchPicker.vue"

const rows = (count: number) =>
  Array.from({length: count}, (_, at) => ({key: `k${at + 1}`, label: `Row ${at + 1}`}))

const open = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.find('[data-testid="pick-search"]').trigger("click")
  await new Promise(resolve => setTimeout(resolve, 0))
  await wrapper.vm.$nextTick()
}

const list = () => document.querySelector('[data-testid="pick-list"]')
const drawn = () => [...(list()?.querySelectorAll(".picker__label") ?? [])].map(el => el.textContent)

const picker = (props: Record<string, unknown> = {}) =>
  mount(SearchPicker, {
    props: {options: rows(6), testidPrefix: "pick", ...props},
    attachTo: document.body,
  })

describe("the picker's own box", () => {
  it("shows what was chosen while it is shut, and what is typed once it is open", async () => {
    const wrapper = picker({selectedKey: "k2"})
    const field = wrapper.find('[data-testid="pick-search"]')

    expect((field.element as HTMLInputElement).value).toBe("Row 2")

    await open(wrapper)
    await field.setValue("Row 4")

    expect((field.element as HTMLInputElement).value).toBe("Row 4")
    expect(drawn()).toEqual(["Row 4"])
    wrapper.unmount()
  })

  it("says there is nothing to choose from, and nothing that answers to the typing", async () => {
    const empty = picker({options: []})
    expect(empty.find('[data-testid="pick-none"]').text()).toBe("There is nothing to choose from.")
    empty.unmount()

    const wrapper = picker()
    await open(wrapper)
    await wrapper.find('[data-testid="pick-search"]').setValue("nothing answers to this")
    await wrapper.vm.$nextTick()

    expect(document.querySelector('[data-testid="pick-no-matches"]')?.textContent?.trim())
      .toBe("Nothing answers to that.")
    wrapper.unmount()
  })

  it("takes its own empty note", () => {
    const wrapper = picker({options: [], emptyNote: "No teams yet."})

    expect(wrapper.find('[data-testid="pick-none"]').text()).toBe("No teams yet.")
    wrapper.unmount()
  })
})

describe("the picker under the keys", () => {
  it("aims the top row, moves with the arrows and stops at either end", async () => {
    const wrapper = picker()
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    await field.setValue("Row")

    await field.trigger("keydown", {key: "ArrowUp"})
    expect(list()?.querySelector(".picker__row--active")?.textContent).toContain("Row 1")

    await field.trigger("keydown", {key: "ArrowDown"})
    await field.trigger("keydown", {key: "ArrowDown"})
    expect(list()?.querySelector(".picker__row--active")?.textContent).toContain("Row 3")
    wrapper.unmount()
  })

  it("takes the aimed row on Enter, and on Tab once something is typed", async () => {
    for (const key of ["Enter", "Tab"]) {
      const wrapper = picker()
      const field = wrapper.find('[data-testid="pick-search"]')
      await open(wrapper)
      await field.setValue("Row 5")
      await field.trigger("keydown", {key})

      expect(wrapper.emitted("pick")?.at(-1)?.[0]).toBe("k5")
      wrapper.unmount()
    }
  })

  it("leaves Tab alone while nothing has been typed", async () => {
    const wrapper = picker()
    await open(wrapper)
    await wrapper.find('[data-testid="pick-search"]').trigger("keydown", {key: "Tab"})

    expect(wrapper.emitted("pick")).toBeUndefined()
    wrapper.unmount()
  })

  it("takes nothing when the typing answers to nothing", async () => {
    const wrapper = picker()
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    await field.setValue("nothing")
    await field.trigger("keydown", {key: "Enter"})
    await field.trigger("keydown", {key: "ArrowDown"})

    expect(wrapper.emitted("pick")).toBeUndefined()
    wrapper.unmount()
  })

  it("closes on Escape, and leaves any other key to the field", async () => {
    const wrapper = picker()
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    expect(list()).not.toBeNull()

    await field.trigger("keydown", {key: "a"})
    expect(list()).not.toBeNull()

    await field.trigger("keydown", {key: "Escape"})
    await wrapper.vm.$nextTick()

    expect(list()).toBeNull()
    wrapper.unmount()
  })
})

describe("the picker's rows", () => {
  it("marks the row already chosen, and reports the one pressed", async () => {
    const wrapper = picker({selectedKey: "k3"})
    await open(wrapper)

    expect(list()?.querySelectorAll(".picker__row--on")).toHaveLength(1)

    const row = document.querySelector('[data-testid="pick-k4"]') as HTMLElement
    row.click()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("pick")?.at(-1)?.[0]).toBe("k4")
    expect(list()).toBeNull()
    wrapper.unmount()
  })

  it("does not come down at all when the field is off", async () => {
    const wrapper = picker({disabled: true})

    await open(wrapper)

    expect(list()).toBeNull()
    wrapper.unmount()
  })

  it("offers no row to press once the field is turned off under it", async () => {
    const wrapper = picker()
    await open(wrapper)

    await wrapper.setProps({disabled: true})
    await wrapper.vm.$nextTick()

    const row = document.querySelector('[data-testid="pick-k1"]') as HTMLButtonElement
    expect(row.disabled).toBe(true)
    wrapper.unmount()
  })

  it("draws a flag and a note where a row carries them", async () => {
    const wrapper = picker({
      options: [{key: "nl", label: "Netherlands", note: "+31", flag: "NL"}],
    })
    await open(wrapper)

    expect(list()?.querySelector(".fi-nl")).not.toBeNull()
    expect(list()?.querySelector(".picker__note-inline")?.textContent).toBe("+31")
    wrapper.unmount()
  })
})

describe("a picker that is told where its rows come from", () => {
  it("reports what is typed, and leaves the filtering alone when it is remote", async () => {
    const wrapper = picker({remote: true})
    await open(wrapper)
    await wrapper.find('[data-testid="pick-search"]').setValue("nothing answers to this")
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("search")?.at(-1)?.[0]).toBe("nothing answers to this")
    expect(drawn()).toHaveLength(6)
    wrapper.unmount()
  })

  it("says when it comes down, so a fetched list knows to fetch", async () => {
    const wrapper = picker()

    await open(wrapper)

    expect(wrapper.emitted("opened")).toHaveLength(1)
    wrapper.unmount()
  })

  it("says it is waiting while the rows are on their way", async () => {
    const wrapper = picker({loading: true})
    await open(wrapper)

    expect(list()?.querySelector(".picker__waiting")?.textContent?.trim()).toBe("Looking")
    wrapper.unmount()
  })
})

describe("the compact picker", () => {
  const compact = (props: Record<string, unknown> = {}) =>
    mount(SearchPicker, {
      props: {options: rows(4), testidPrefix: "pick", compact: true, selectedKey: "k1", ...props},
      slots: {chosen: '<span class="chosen">{{ params.option?.label ?? "none" }}</span>'},
      attachTo: document.body,
    })

  it("is a button that says what was chosen, with the search inside the list", async () => {
    const wrapper = compact()

    expect(wrapper.find(".chosen").text()).toBe("Row 1")
    expect(wrapper.find('[data-testid="pick-search"]').exists()).toBe(false)

    await wrapper.find('[data-testid="pick-shut"]').trigger("click")
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    expect(document.querySelector('[data-testid="pick-search"]')).not.toBeNull()
    wrapper.unmount()
  })

  it("puts the list away when it is pressed again", async () => {
    const wrapper = compact()
    const trigger = wrapper.find('[data-testid="pick-shut"]')

    await trigger.trigger("click")
    await wrapper.vm.$nextTick()
    expect(list()).not.toBeNull()

    await trigger.trigger("click")
    await wrapper.vm.$nextTick()

    expect(list()).toBeNull()
    wrapper.unmount()
  })

  it("is off when the field is", () => {
    const wrapper = compact({disabled: true})

    expect((wrapper.find('[data-testid="pick-shut"]').element as HTMLButtonElement).disabled)
      .toBe(true)
    wrapper.unmount()
  })
})

describe("a picker that stays open", () => {
  it("empties the box and keeps the list open after a pick, so the next row can follow", async () => {
    const wrapper = picker({stayOpen: true})
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    await field.setValue("Row 2")

    ;(document.querySelector('[data-testid="pick-k2"]') as HTMLElement).click()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("pick")).toEqual([["k2"]])
    expect(list()).not.toBeNull()
    expect((field.element as HTMLInputElement).value).toBe("")
    expect(document.activeElement).toBe(field.element)
    wrapper.unmount()
  })

  it("takes the row a comma completes, and types the comma where nothing answers", async () => {
    const wrapper = picker({stayOpen: true})
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    await field.setValue("Row 3")

    await field.trigger("keydown", {key: ","})
    expect(wrapper.emitted("pick")).toEqual([["k3"]])
    expect(list()).not.toBeNull()

    await field.setValue("nothing")
    const comma = new KeyboardEvent("keydown", {key: ",", cancelable: true})
    field.element.dispatchEvent(comma)
    expect(wrapper.emitted("pick")).toEqual([["k3"]])
    expect(comma.defaultPrevented).toBe(false)
    wrapper.unmount()
  })

  it("closes behind Tab, so Tab still moves on", async () => {
    const wrapper = picker({stayOpen: true})
    const field = wrapper.find('[data-testid="pick-search"]')
    await open(wrapper)
    await field.setValue("Row 5")

    await field.trigger("keydown", {key: "Tab"})

    expect(wrapper.emitted("pick")).toEqual([["k5"]])
    expect(list()).toBeNull()
    wrapper.unmount()
  })

  it("closes after a pick where it is not told to stay open", async () => {
    const wrapper = picker()
    await open(wrapper)
    ;(document.querySelector('[data-testid="pick-k2"]') as HTMLElement).click()
    await wrapper.vm.$nextTick()

    expect(list()).toBeNull()
    wrapper.unmount()
  })
})
