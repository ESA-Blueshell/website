import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import BoardList from "@/pages/management/BoardList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findAllBoards: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const board = (number: number, startDate: string, endDate: string | null, members: unknown[], name?: string) => ({
  id: number, number, startDate, endDate, members, name, candidate: "", createdAt: "2020-01-01T00:00:00Z", updatedAt: "2020-01-01T00:00:00Z", version: 0,
})

describe("the boards in Management", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(BoardList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date("2026-03-01T12:00:00Z"))
    vi.clearAllMocks()
    api.findAllBoards.mockResolvedValue({status: 200, data: [
      board(12, "2026-09-01", null, [{id: 1, name: "Kim Vos"}]),
      board(11, "2025-09-01", null, [{id: 2, name: "Alice"}, {id: 3, name: "Bram"}], "The Golden Board"),
      board(10, "2024-09-01", "2025-08-31", []),
    ]})
  })

  afterEach(() => {
    unmountAll(wrappers, "BoardListPage")
    vi.useRealTimers()
  })

  it("lists every board with its year, members and where it stands, each opening its editor", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="board-row-12"]').text()).toContain("1 member")
    expect(wrapper.get('[data-testid="board-standing-12"]').text()).toBe("Kandi")
    expect(wrapper.get('[data-testid="board-row-11"]').text()).toContain("The Golden Board")
    expect(wrapper.get('[data-testid="board-row-11"]').text()).toContain("2025-2026")
    expect(wrapper.get('[data-testid="board-standing-11"]').text()).toBe("In office")
    expect(wrapper.get('[data-testid="board-standing-10"]').text()).toBe("Handed over")
    expect(wrapper.get('[data-testid="board-row-10"]').text()).toContain("0 members")
    expect(wrapper.get('[data-testid="board-row-10"] a').attributes("to")).toBe("/management/board/10")
  })

  it("narrows by a name, a member or a year, and says when nothing matches", async () => {
    const wrapper = await mount()

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "bram")
    await settle()
    expect(wrapper.findAll('[data-testid^="board-row-"]').map((one) => one.attributes("data-testid"))).toEqual(["board-row-11"])

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "1999")
    await settle()
    expect(wrapper.get('[data-testid="board-list-empty"]').text()).toBe("No board matches.")
  })
})
