import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SeatRows from "@/components/island/SeatRows.vue"

/**
 * The rows draw whatever they are handed, which is why they can be mounted at all: nothing in
 * here is a Vuetify component, so the stubbed plugin the unit suite runs without takes nothing
 * with it. What a reader sees of it — the diagonal, the alignment of the column, the stacked
 * layout — is the end-to-end suite's, because jsdom lays nothing out.
 */
const rows = [
  {id: 91, name: 'Emma "Emmz" Dokter', role: "Chair", blurb: "Chairing the ninth board.", portrait: "/emma.webp", srcset: "/emma-160.webp 160w"},
  {id: 92, name: "Viktor Petrov", role: "Treasurer", blurb: "Counting it."},
  {id: 93, name: "Roos Kruk", role: "Commissioner of Internal Affairs"},
]

const mountRows = (over: Partial<{rows: typeof rows}> = {}) =>
  mount(SeatRows, {props: {rows, accent: "#3387fa", testidPrefix: "board", ...over}})

const row = (wrapper: ReturnType<typeof mountRows>, id: number) =>
  wrapper.get(`[data-testid="board-seat-${id}"]`)

describe("SeatRows", () => {
  it("names each row, its name and its role by the prefix its page uses", () => {
    const wrapper = mountRows()

    expect(wrapper.get('[data-testid="board-seat-name-91"]').text()).toBe('Emma "Emmz" Dokter')
    expect(wrapper.get('[data-testid="board-seat-role-91"]').text()).toBe("Chair")
    expect(wrapper.get('[data-testid="board-seat-name-93"]').text()).toBe("Roos Kruk")
  })

  it("draws the portrait where there is one, at the widths it is stored at", () => {
    const wrapper = mountRows()

    const portrait = wrapper.get('[data-testid="board-seat-portrait-91"]')
    expect(portrait.attributes("src")).toBe("/emma.webp")
    expect(portrait.attributes("srcset")).toBe("/emma-160.webp 160w")
    // A plate is drawn at 88 css pixels at the most, so the master is never what it needs.
    expect(portrait.attributes("sizes")).toBe("88px")
  })

  it("draws the initials where there is no portrait, so the column stays a column", () => {
    const wrapper = mountRows()

    expect(wrapper.find('[data-testid="board-seat-portrait-92"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="board-seat-monogram-92"]').text()).toBe("VP")
    // The nickname is not the person, so it is not on the plate either.
    expect(wrapper.find('[data-testid="board-seat-monogram-91"]').exists()).toBe(false)
  })

  it("offers a chevron only where something was written about the seat", () => {
    const wrapper = mountRows()

    expect(wrapper.find('[data-testid="board-seat-chevron-91"]').exists()).toBe(true)
    // Nobody wrote anything about the third seat, so it does not offer to be opened.
    expect(wrapper.find('[data-testid="board-seat-chevron-93"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="board-seat-blurb-93"]').exists()).toBe(false)
    expect(row(wrapper, 93).find("button").exists()).toBe(false)
  })

  it("opens the first row with something written about it, which is the chair's", () => {
    const wrapper = mountRows()

    expect(row(wrapper, 91).classes()).toContain("seat-row--open")
    expect(row(wrapper, 91).get("button").attributes("aria-expanded")).toBe("true")
    expect(row(wrapper, 92).classes()).not.toContain("seat-row--open")
  })

  it("opens one row at a time", async () => {
    const wrapper = mountRows()

    await row(wrapper, 92).get("button").trigger("click")

    expect(row(wrapper, 92).classes()).toContain("seat-row--open")
    expect(row(wrapper, 91).classes()).not.toContain("seat-row--open")
  })

  it("shuts the open row again on the gesture that opened it", async () => {
    const wrapper = mountRows()

    await row(wrapper, 91).get("button").trigger("click")

    expect(row(wrapper, 91).classes()).not.toContain("seat-row--open")
    expect(row(wrapper, 91).get("button").attributes("aria-expanded")).toBe("false")
  })

  it("keeps a shut row's words out of what a screen reader is told", () => {
    const wrapper = mountRows()

    expect(wrapper.get('[data-testid="board-seat-blurb-91"]').attributes("aria-hidden")).toBeUndefined()
    expect(wrapper.get('[data-testid="board-seat-blurb-92"]').attributes("aria-hidden")).toBe("true")
  })

  it("opens nothing where nobody wrote anything about anybody", () => {
    // A whole board of the history is like this: portraits and no blurbs at all.
    const wrapper = mountRows({rows: [
      {id: 61, name: "Roos Kruk", role: "Chair"},
      {id: 62, name: "Thijs Lieverse", role: "Treasurer"},
    ]})

    expect(wrapper.findAll(".seat-row--open")).toHaveLength(0)
    expect(wrapper.findAll("button")).toHaveLength(0)
  })

  it("opens the chair of the board it is handed next, not the row that was open before", async () => {
    const wrapper = mountRows()
    await row(wrapper, 92).get("button").trigger("click")

    await wrapper.setProps({rows: [
      {id: 71, name: "Thijs Lieverse", role: "Chairman", blurb: "Overcooked."},
      {id: 72, name: "Anne Schrader", role: "Secretary", blurb: "Writing it down."},
    ]})

    // A different set is a different board, and the row that was open belonged to the one before.
    expect(row(wrapper, 71).classes()).toContain("seat-row--open")
    expect(row(wrapper, 72).classes()).not.toContain("seat-row--open")
  })
})
