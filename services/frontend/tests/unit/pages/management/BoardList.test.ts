import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import BoardList from "@/pages/management/BoardList.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findAllBoards: vi.fn(), findCohorts: vi.fn(), listDiscordMatches: vi.fn(), adoptDiscordMatches: vi.fn()}))

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
    api.findCohorts.mockResolvedValue({status: 200, data: [
      cohort("BOARD", [{system: "DISCORD", label: "Board", made: true, externalId: "77"}, {system: "BREVO", label: "Board list", made: true, externalId: "5"}]),
      cohort("KANDI", [{system: "DISCORD", label: "Kandi", made: true, externalId: "78"}]),
      cohort("BOARD_YEAR_MEMBERS:10", [{system: "DISCORD", label: "Board 2024-2025", made: true, externalId: "70"}]),
    ]})
  })

  const cohort = (definitionKey: string, targets: unknown[]) =>
    ({id: 1, type: definitionKey, category: "BOARD", label: definitionKey, memberCount: 0, mappingCount: targets.length, definitionKey, targets})

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
    expect(await sortByEveryHead(wrapper)).toBe(6)
  })

  it("links the role and list of the board in office and of the candidate board, and the role of its years for a board that handed over", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="board-discord-11"]').text()).toBe("@Board")
    expect(wrapper.get('[data-testid="board-discord-11"]').attributes("to")).toBe("/management/platforms/discord/roles/77")
    expect(wrapper.get('[data-testid="board-brevo-11"]').attributes("to")).toBe("/management/platforms/brevo/lists/5")
    expect(wrapper.get('[data-testid="board-discord-12"]').attributes("to")).toBe("/management/platforms/discord/roles/78")
    expect(wrapper.get('[data-testid="board-brevo-12"]').text()).toBe("No list")
    expect(wrapper.get('[data-testid="board-discord-10"]').text()).toBe("@Board 2024-2025")
    expect(wrapper.get('[data-testid="board-discord-10"]').attributes("to")).toBe("/management/platforms/discord/roles/70")
    expect(wrapper.get('[data-testid="board-brevo-10"]').text()).toBe("No list")

    api.findCohorts.mockRejectedValue(new Error("offline"))
    expect((await mount()).get('[data-testid="board-discord-11"]').text()).toBe("No role")
  })

  it("draws each board as a row on a phone, and names the board in office among the facts", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.findAllComponents({name: "ManagementRow"}).length).toBeGreaterThan(0)
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("In office")
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

  it("links the ticked boards to the role that carries their years, and leaves out the rest", async () => {
    api.findAllBoards.mockResolvedValue({status: 200, data: [
      board(11, "2025-09-01", null, [{id: 2, name: "Alice"}]),
      board(10, "2024-09-01", "2025-08-31", []),
      board(9, "2023-09-01", "2024-08-31", []),
      board(8, "2022-09-01", "2023-08-31", []),
    ]})
    api.listDiscordMatches.mockResolvedValue({status: 200, data: [
      {key: "BOARD_YEAR_MEMBERS:9", label: "Board 2023-2024", type: "BOARD_YEAR_MEMBERS", roleId: "69", roleName: "Board 2023-2024", channels: []},
    ]})
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 1, refused: []}})
    const wrapper = await mount()
    const bulk = () => wrapper.findComponent({name: "BulkAdd"})

    // The head's own offer to take every row, shown or not, and to let go again.
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("selectAll")
    await settle()
    expect(wrapper.get('[data-testid="board-list-selection"]').exists()).toBe(true)
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("clearSelection")
    await settle()
    for (const number of [10, 9, 8]) await wrapper.get(`[data-testid="board-check-${number}"]`).setValue(true)
    await wrapper.get('[data-testid="board-link-roles"]').trigger("click")
    await settle()

    expect(bulk().props("open")).toBe(true)
    expect(bulk().props("items").map((one: {name: string; note: string}) => [one.name, one.note])).toEqual([["Board IX", "@Board 2023-2024"]])
    expect(bulk().props("skipped")).toEqual([
      {name: "Board X", why: "Has a role already"},
      {name: "Board VIII", why: "No role on Discord carries its name"},
    ])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.adoptDiscordMatches).toHaveBeenCalledWith({body: {keys: ["BOARD_YEAR_MEMBERS:9"]}})
    api.adoptDiscordMatches.mockResolvedValue({status: 503, error: {message: "Discord is away."}})
    expect((await bulk().props("run")(bulk().props("items")[0])).ok).toBe(false)
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 0, refused: [{label: "Board 2023-2024", reason: "The bot may not change it."}]}})
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: false, reason: "The bot may not change it."})

    api.findCohorts.mockClear()
    bulk().vm.$emit("done")
    await settle()
    expect(api.findCohorts).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="board-list-selection"]').text()).toContain("0 selected")
    expect(wrapper.get('[data-testid="board-link-roles"]').element.closest("fieldset")?.disabled).toBe(true)
    bulk().vm.$emit("update:open", false)
    await settle()
    expect(bulk().props("open")).toBe(false)
  })
})
