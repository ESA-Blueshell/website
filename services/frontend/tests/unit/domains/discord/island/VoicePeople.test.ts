import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import VoicePeople from "@/domains/discord/island/VoicePeople.vue"

const people = ["Emma", "Viktor", "Mo", "Ana", "Kai"].map(name => ({name}))

/* jsdom lays nothing out, so the row and every person in it are given widths. */
const lay = (row: number, person: number, more: number) => {
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(row)
  vi.spyOn(HTMLElement.prototype, "offsetWidth", "get").mockImplementation(function (this: HTMLElement) {
    return this.classList.contains("people__more") ? more : person
  })
}

/* What the row draws, not the hidden layer it is measured from. */
const drawn = (wrapper: ReturnType<typeof mount>, selector: string) =>
  [...wrapper.element.querySelectorAll<HTMLElement>(`:scope > ${selector}`)]
const names = (wrapper: ReturnType<typeof mount>) =>
  drawn(wrapper, ".people__person .people__name").map(one => one.textContent)
const more = (wrapper: ReturnType<typeof mount>) => drawn(wrapper, ".people__more")[0]?.textContent

afterEach(() => {
  vi.restoreAllMocks()
})

describe("VoicePeople", () => {
  it("names everybody where they all fit, with an avatar or an initial each", () => {
    const wrapper = mount(VoicePeople, {props: {people: [{name: "emma", avatar: "/e.png"}, {name: "viktor"}]}})

    expect(names(wrapper)).toEqual(["emma", "viktor"])
    expect(drawn(wrapper, ".people__person img")[0]?.getAttribute("src")).toBe("/e.png")
    expect(wrapper.get(".people__avatar--initial").text()).toBe("V")
    expect(more(wrapper)).toBeUndefined()
  })

  it("names as many as fit on the line, then how many more", async () => {
    lay(200, 50, 60)
    const wrapper = mount(VoicePeople, {props: {people}})
    await flushPromises()

    expect(names(wrapper)).toEqual(["Emma", "Viktor"])
    expect(more(wrapper)).toBe("and 3 more")
  })

  it("fits the row again when the people change", async () => {
    lay(200, 50, 60)
    const wrapper = mount(VoicePeople, {props: {people}})
    await wrapper.setProps({people: people.slice(0, 3)})
    await flushPromises()

    expect(names(wrapper)).toEqual(["Emma", "Viktor", "Mo"])
    expect(more(wrapper)).toBeUndefined()
  })
})
