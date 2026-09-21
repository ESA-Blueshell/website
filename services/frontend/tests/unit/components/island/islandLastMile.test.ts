import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {CompletionContext} from "@codemirror/autocomplete"
import {emojiCompletion} from "@/components/island/markdownEmoji"
import IslandPicker from "@/components/island/IslandPicker.vue"

const rows = (many: number) => Array.from({length: many}, (_, at) => ({
  key: `k${at}`,
  label: `Row ${at}`,
}))

const openIt = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.find("input").trigger("click")
  await flushPromises()
}

afterEach(() => {
  document.body.innerHTML = ""
})

describe("the list a picker drops", () => {
  it("keeps the press that lands on itself", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)

    const list = document.querySelector("[data-island-picker-list]") as HTMLElement
    list.dispatchEvent(new MouseEvent("mousedown", {bubbles: true}))
    list.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    list.dispatchEvent(new MouseEvent("mousemove", {bubbles: true}))
    await flushPromises()

    expect(document.querySelector("[data-island-picker-list]")).not.toBeNull()
  })

  it("shows the row under the pointer, and stops when it leaves", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
      slots: {lead: "<i>{{ params.option?.key ?? 'none' }}</i>"},
    })
    await openIt(wrapper)

    const row = document.querySelector("[data-testid='p-k1']") as HTMLElement
    row.dispatchEvent(new MouseEvent("mouseenter", {bubbles: true}))
    await flushPromises()
    expect(wrapper.find("i").text()).toBe("k1")

    row.dispatchEvent(new MouseEvent("mouseleave", {bubbles: true}))
    await flushPromises()
    expect(wrapper.find("i").text()).toBe("none")
  })

  it("leaves the pointer alone once the keys are aiming", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
      slots: {lead: "<i>{{ params.option?.key ?? 'none' }}</i>"},
    })
    await openIt(wrapper)
    await wrapper.find("input").setValue("Row")
    await wrapper.find("input").trigger("keydown", {key: "ArrowDown"})

    const row = document.querySelector("[data-testid='p-k2']") as HTMLElement
    row.dispatchEvent(new MouseEvent("mouseenter", {bubbles: true}))
    await flushPromises()

    expect(wrapper.find("i").text()).toBe("k1")
  })

  it("stays down when a press lands on the panel drawn outside it", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)

    const row = document.querySelector("[data-testid='p-k1']") as HTMLElement
    document.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    Object.defineProperty(document, "activeElement", {configurable: true, value: row})
    await flushPromises()

    expect(document.querySelector("[data-island-picker-list]")).toBeNull()
  })

  it("holds the press the list itself answers", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)

    const row = document.querySelector("[data-testid='p-k1']") as HTMLElement
    const press = new MouseEvent("pointerdown", {bubbles: true})
    row.dispatchEvent(press)
    document.dispatchEvent(Object.assign(new MouseEvent("pointerdown"), {}))
    await flushPromises()

    expect(wrapper.emitted("pick")).toBeUndefined()
  })

  it("says nothing answers, and keeps the press on that line too", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)
    await wrapper.find("input").setValue("nothing like it")
    await flushPromises()

    const note = document.querySelector("[data-testid='p-no-matches']") as HTMLElement
    expect(note).not.toBeNull()
    note.dispatchEvent(new MouseEvent("mousedown", {bubbles: true}))
    note.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()

    expect(document.querySelector("[data-testid='p-no-matches']")).not.toBeNull()
  })

  it("aims nothing while the list holds nothing to aim at", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)
    await wrapper.find("input").setValue("nothing like it")
    await wrapper.find("input").trigger("keydown", {key: "ArrowDown"})
    await wrapper.find("input").trigger("keydown", {key: "Enter"})

    expect(wrapper.emitted("pick")).toBeUndefined()
  })

  it("stays down while focus moves to something inside it", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)

    const inside = wrapper.find("input").element
    await wrapper.find(".picker").trigger("focusout", {relatedTarget: inside})
    await flushPromises()

    expect(document.querySelector("[data-island-picker-list]")).not.toBeNull()
  })

  it("measures nothing where there is no list to measure", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: [], testidPrefix: "p"},
    })
    await openIt(wrapper)

    expect(document.querySelector("[data-island-picker-list]")).toBeNull()
  })

  it("aims without a list where the keys ask before it is down", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })

    await wrapper.find("input").trigger("keydown", {key: "ArrowUp"})

    expect(document.querySelector("[data-island-picker-list]")).toBeNull()
  })

  it("takes the height of the one row it was given", async () => {
    const tall = vi.spyOn(Element.prototype, "getBoundingClientRect")
      .mockReturnValue({bottom: 0, height: 40, left: 0, top: 0, width: 100} as DOMRect)
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(1), testidPrefix: "p"},
    })
    await openIt(wrapper)
    tall.mockRestore()

    expect(document.querySelectorAll("[data-testid^='p-k']")).toHaveLength(1)
  })

  it("takes the step between two rows where the browser has laid them out", async () => {
    const tops = vi.spyOn(HTMLElement.prototype, "offsetTop", "get")
    let at = 0
    tops.mockImplementation(() => (at += 52) - 52)
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {options: rows(3), testidPrefix: "p"},
    })
    await openIt(wrapper)
    tops.mockRestore()

    expect(document.querySelectorAll("[data-testid^='p-k']")).toHaveLength(3)
  })
})

describe("a picker with room for nothing but its own button", () => {
  it("shows what was chosen until something is typed", async () => {
    const wrapper = mount(IslandPicker, {
      attachTo: document.body,
      props: {compact: true, options: rows(3), selectedKey: "k1", testidPrefix: "c"},
    })
    await wrapper.find("[data-testid='c-shut']").trigger("click")
    await flushPromises()

    const search = document.querySelector("[data-testid='c-search']") as HTMLInputElement
    expect(search.value).toBe("Row 1")

    search.value = "Row 2"
    search.dispatchEvent(new Event("input", {bubbles: true}))
    await flushPromises()

    expect((document.querySelector("[data-testid='c-search']") as HTMLInputElement).value)
      .toBe("Row 2")
  })
})

describe("the emoji list", () => {
  it("answers a bare colon where it was asked for outright", () => {
    const asked = {
      matchBefore: () => ({from: 4, to: 4, text: ":"}),
      explicit: true,
    } as unknown as CompletionContext

    expect(emojiCompletion(asked)).not.toBeNull()
  })
})

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: vi.fn()}))
