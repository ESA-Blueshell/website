import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SearchPicker from "@/components/island/SearchPicker.vue"

const options = (count: number) =>
  Array.from({length: count}, (_, at) => ({key: `k${at + 1}`, label: `Member ${at + 1}`}))

const picker = (count: number) =>
  mount(SearchPicker, {
    props: {options: options(count), testidPrefix: "pick"},
    attachTo: document.body,
  })

const open = async (wrapper: ReturnType<typeof picker>) => {
  await wrapper.find('[data-testid="pick-search"]').trigger("click")
  await new Promise(resolve => setTimeout(resolve, 0))
  await wrapper.vm.$nextTick()
}

const drawn = () => document.querySelectorAll('[data-testid^="pick-k"]').length

describe("SearchPicker", () => {
  it("draws every row of a short list, so tab reaches all of them", async () => {
    const wrapper = picker(20)

    await open(wrapper)

    expect(drawn()).toBe(20)
    wrapper.unmount()
  })

  it("draws a window of a long one rather than thousands of rows", async () => {
    const wrapper = picker(2400)

    await open(wrapper)

    const rows = drawn()
    expect(rows).toBeGreaterThan(0)
    expect(rows).toBeLessThan(60)
    wrapper.unmount()
  })

  it("keeps the full height under the window, so the scrollbar tells the truth", async () => {
    const wrapper = picker(2400)

    await open(wrapper)

    const list = document.querySelector('[data-testid="pick-list"]') as HTMLElement
    const pads = [...list.querySelectorAll("[data-picker-pad]")] as HTMLElement[]
    const padding = pads.reduce((sum, pad) => sum + Number.parseInt(pad.style.height, 10), 0)
    expect(padding).toBeGreaterThan(2400 * 20)
    wrapper.unmount()
  })

  it("narrows to what was typed", async () => {
    const wrapper = picker(2400)
    await open(wrapper)

    const field = wrapper.find('[data-testid="pick-search"]')
    await field.setValue("Member 1234")
    await wrapper.vm.$nextTick()

    expect(drawn()).toBe(1)
    wrapper.unmount()
  })
})

describe("SearchPicker's search", () => {
  const rows = [
    {key: "fr", label: "French", note: "FR", terms: ["France", "Française", "FRA"]},
    {key: "de", label: "German", note: "DE", terms: ["Germany", "Deutschland"]},
  ]

  const typed = async (into: string) => {
    const wrapper = mount(SearchPicker, {
      props: {options: rows, testidPrefix: "pick"},
      attachTo: document.body,
    })
    const field = wrapper.find('[data-testid="pick-search"]')
    await field.trigger("click")
    await field.setValue(into)
    await wrapper.vm.$nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))
    const list = document.querySelector('[data-testid="pick-list"]')
    const drawn = [...(list?.querySelectorAll(".picker__label") ?? [])].map(el => el.textContent)
    wrapper.unmount()
    return drawn
  }

  it("finds a row by a word it does not draw", async () => {
    expect(await typed("France")).toEqual(["French"])
    expect(await typed("deutschland")).toEqual(["German"])
  })

  it("ignores the accents on either side", async () => {
    expect(await typed("francaise")).toEqual(["French"])
  })

  it("still finds a row by what it says", async () => {
    expect(await typed("german")).toEqual(["German"])
    expect(await typed("fr")).toEqual(["French"])
  })

  it("draws a row's avatar before its name", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: [{key: "803", label: "Nelly B", avatar: "https://cdn/nelly.png"}], testidPrefix: "pick"},
      attachTo: document.body,
    })

    await open(wrapper)

    const row = document.querySelector('[data-testid="pick-803"]') as HTMLElement
    expect(row.querySelector("img")?.getAttribute("src")).toBe("https://cdn/nelly.png")
    wrapper.unmount()
  })

  it("draws the chosen row's avatar in the box, and none with nothing chosen", async () => {
    const nelly = {key: "803", label: "Nelly B", avatar: "https://cdn/nelly.png"}
    const wrapper = mount(SearchPicker, {
      props: {options: [nelly], testidPrefix: "pick", selectedKey: null},
      attachTo: document.body,
    })
    expect(wrapper.find('[data-testid="pick-avatar"]').exists()).toBe(false)

    await wrapper.setProps({selectedKey: "803"})

    expect(wrapper.find('[data-testid="pick-avatar"]').attributes("src")).toBe("https://cdn/nelly.png")
    wrapper.unmount()
  })

  it("shows the first search in the shut box, marked as no choice yet, until a row is chosen", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: options(3), testidPrefix: "pick", remote: true, firstSearch: "nelly"},
      attachTo: document.body,
    })
    const box = () => wrapper.find('[data-testid="pick-search"]').element as HTMLInputElement

    expect(box().value).toBe("nelly")
    expect(wrapper.find(".picker__field").classes()).toContain("picker__field--unmatched")

    await wrapper.setProps({selectedKey: "k1"})
    expect(box().value).toBe("Member 1")
    expect(wrapper.find(".picker__field").classes()).not.toContain("picker__field--unmatched")
    wrapper.unmount()
  })

  it("types the first search for the reader each time the list opens with nothing chosen, and searches it", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: options(3), testidPrefix: "pick", remote: true, firstSearch: "nelly"},
      attachTo: document.body,
    })

    await open(wrapper)
    expect((wrapper.find('[data-testid="pick-search"]').element as HTMLInputElement).value).toBe("nelly")
    expect(wrapper.find(".picker__field").classes()).not.toContain("picker__field--unmatched")
    expect(wrapper.emitted("search")).toEqual([["nelly"]])

    await wrapper.find('[data-testid="pick-search"]').trigger("keydown", {key: "Escape"})
    await open(wrapper)
    expect((wrapper.find('[data-testid="pick-search"]').element as HTMLInputElement).value).toBe("nelly")
    expect(wrapper.emitted("search")).toEqual([["nelly"], ["nelly"]])
    wrapper.unmount()
  })

  it("does not type the first search over a choice already made", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: options(3), testidPrefix: "pick", remote: true, firstSearch: "nelly", selectedKey: "k1"},
      attachTo: document.body,
    })

    await open(wrapper)

    expect(wrapper.emitted("search")).toBeUndefined()
    wrapper.unmount()
  })

  it("says what the caller gives it when a search found nothing, and no empty note when told none", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: [], testidPrefix: "pick", remote: true, emptyNote: ""},
      slots: {missing: "Not in the server yet."},
      attachTo: document.body,
    })
    expect(wrapper.find('[data-testid="pick-none"]').exists()).toBe(false)

    await open(wrapper)

    expect(document.querySelector('[data-testid="pick-missing"]')?.textContent).toContain("Not in the server yet.")
    wrapper.unmount()
  })

  it("says the choice was taken away when the box is emptied and left, and not when it was only looked at", async () => {
    const wrapper = mount(SearchPicker, {
      props: {options: options(3), testidPrefix: "pick", selectedKey: "k1"},
      attachTo: document.body,
    })
    const leave = async () => {
      await wrapper.find(".picker").trigger("focusout", {relatedTarget: document.body})
      await new Promise(resolve => setTimeout(resolve, 0))
    }

    await open(wrapper)
    await leave()
    expect(wrapper.emitted("clear")).toBeUndefined()

    await open(wrapper)
    await wrapper.find('[data-testid="pick-search"]').setValue("")
    await leave()

    expect(wrapper.emitted("clear")).toHaveLength(1)
    wrapper.unmount()
  })

  it("rests on a quiet line until something is chosen", async () => {
    const empty = mount(SearchPicker, {props: {options: options(2), testidPrefix: "pick"}})
    const chosen = mount(SearchPicker, {props: {options: options(2), testidPrefix: "pick", selectedKey: "k1"}})

    expect(empty.find(".picker__field").classes()).not.toContain("picker__field--chosen")
    expect(chosen.find(".picker__field").classes()).toContain("picker__field--chosen")
  })
})

