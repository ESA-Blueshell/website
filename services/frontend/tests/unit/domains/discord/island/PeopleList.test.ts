import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import PeopleList from "@/domains/discord/island/PeopleList.vue"

const people = [
  {name: "Nelly", avatar: "https://cdn/n.png", discord: true, role: "Chair"},
  {name: "lars", avatar: null, discord: false},
  {name: "Mo", discord: true},
]

describe("a list of people", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("names a Discord-linked person as Discord does, with their picture, and anybody else by username without one", () => {
    const wrapper = mount(PeopleList, {props: {people, testid: "people"}})

    expect(wrapper.get('[data-testid="people-0"]').text()).toContain("@Nelly")
    expect(wrapper.get('[data-testid="people-0"]').text()).toContain("Chair")
    expect(wrapper.get('[data-testid="people-0"] img').attributes("src")).toBe("https://cdn/n.png")
    expect(wrapper.get('[data-testid="people-1"]').text()).toBe("lars")
    expect(wrapper.find('[data-testid="people-1"] img').exists()).toBe(false)
    expect(wrapper.find('[data-testid="people-more"]').exists()).toBe(false)
  })

  it("keeps to its first row on asking, offers the rest as and N more, and opens them", async () => {
    type Watch = () => void
    let watched: Watch | undefined
    vi.stubGlobal("ResizeObserver", class {
      constructor(callback: Watch) {
        watched = callback
      }
      observe() {}
      disconnect() {}
    })
    // jsdom lays nothing out, so the second and third person are put on a row of their own here.
    const tops = [0, 40, 40]
    const spy = vi.spyOn(HTMLElement.prototype, "offsetTop", "get").mockImplementation(function (this: HTMLElement) {
      return tops[[...this.parentElement!.children].indexOf(this)] ?? 0
    })
    const wrapper = mount(PeopleList, {props: {people, oneRow: true, testid: "people"}})
    await flushPromises()

    expect(wrapper.get('[data-testid="people-more"]').text()).toBe("and 2 more")
    watched!()
    await wrapper.setProps({people: [...people, {name: "Ann", discord: false}]})
    tops.push(40)
    await flushPromises()
    expect(wrapper.get('[data-testid="people-more"]').text()).toBe("and 3 more")

    await wrapper.get('[data-testid="people-more"]').trigger("click")
    await flushPromises()
    expect(wrapper.find('[data-testid="people-more"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="people"]').classes()).not.toContain("people__list--one-row")
    wrapper.unmount()
    spy.mockRestore()
  })
})
