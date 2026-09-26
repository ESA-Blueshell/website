import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {ref} from "vue"
import GameHandles from "@/domains/esports/island/GameHandles.vue"
import {settle} from "../../../pages/helpers"

const {mockAdapters, mockNetworkError, games} = vi.hoisted(() => ({
  mockAdapters: {loadGameAccounts: vi.fn(), saveGameAccount: vi.fn(), dropGameAccount: vi.fn()},
  mockNetworkError: vi.fn(),
  games: [
    {code: "VALORANT", name: "Valorant", current: true},
    {code: "LEAGUE", name: "League of Legends", current: true},
    {code: "CSGO", name: "CS:GO", current: false},
  ],
}))

vi.mock("@/domains/esports/adapters/esports", () => mockAdapters)
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({
    games: ref(games),
    identityOf: (code: string) => ({
      name: "", accent: "#ff4655", banner: null,
      icon: code === "VALORANT" ? "/valorant.webp" : null,
      iconSrcset: code === "VALORANT" ? "/valorant-88.webp 88w" : undefined,
    }),
  }),
}))

const open = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(GameHandles, {props: {userId: 7, ...props}})
  await settle()
  return wrapper
}
type Handles = Awaited<ReturnType<typeof open>>
const field = (wrapper: Handles, code: string) => wrapper.get(`[data-testid=game-handle-${code}] input`)
const saveButton = (wrapper: Handles, code: string) => wrapper.find(`[data-testid=game-handle-save-${code}]`)
const submit = async (wrapper: Handles, code: string) => {
  await field(wrapper, code).element.closest("form")!.dispatchEvent(new Event("submit"))
  await settle()
}

describe("the game handles", () => {
  beforeEach(() => {
    mockAdapters.loadGameAccounts.mockResolvedValue([{game: "VALORANT", handle: "alice#EUW"}])
    mockAdapters.saveGameAccount.mockResolvedValue(null)
    mockAdapters.dropGameAccount.mockResolvedValue(undefined)
  })

  it("lists the games fielded now before the rest, each with its handle", async () => {
    const wrapper = await open()

    expect(wrapper.findAll("h2").map(one => one.text().replace(/[\s\u2060]+/gu, " "))).toEqual(["Fielded this season 2games", "Other games 1games"])
    expect(wrapper.findAll(".handle__name").map(one => one.text())).toEqual(["Valorant", "League of Legends", "CS:GO"])
    expect((field(wrapper, "valorant").element as HTMLInputElement).value).toBe("alice#EUW")
    expect(wrapper.find(".handle__glyph img").attributes("src")).toBe("/valorant.webp")
    expect(wrapper.findAll(".handle__glyph")[1]!.text()).toBe("L")
  })

  it("saves a changed handle, and says so until it is edited again", async () => {
    mockAdapters.loadGameAccounts
      .mockResolvedValueOnce([{game: "VALORANT", handle: "alice#EUW"}])
      .mockResolvedValue([{game: "VALORANT", handle: "alice#EUW"}, {game: "LEAGUE", handle: "Alice"}])
    const wrapper = await open()
    expect(saveButton(wrapper, "league").exists()).toBe(false)

    await field(wrapper, "league").setValue("  Alice ")
    expect(saveButton(wrapper, "league").exists()).toBe(true)
    await submit(wrapper, "league")

    expect(mockAdapters.saveGameAccount).toHaveBeenCalledWith(7, "LEAGUE", "Alice")
    expect(saveButton(wrapper, "league").exists()).toBe(false)
    expect(wrapper.text()).toContain("Saved")

    await field(wrapper, "league").setValue("Alice2")
    expect(saveButton(wrapper, "league").exists()).toBe(true)
  })

  it("removes a handle that is emptied, and does nothing for one left as it was", async () => {
    const wrapper = await open()

    await submit(wrapper, "league")
    expect(mockAdapters.saveGameAccount).not.toHaveBeenCalled()

    await field(wrapper, "valorant").setValue("")
    await submit(wrapper, "valorant")
    expect(mockAdapters.dropGameAccount).toHaveBeenCalledWith(7, "VALORANT")
  })

  it("reports a refused save or read", async () => {
    mockAdapters.saveGameAccount.mockRejectedValue(new Error("refused"))
    const wrapper = await open()

    await field(wrapper, "league").setValue("Alice")
    await submit(wrapper, "league")
    expect(mockNetworkError).toHaveBeenCalled()

    mockAdapters.loadGameAccounts.mockRejectedValue(new Error("down"))
    await wrapper.setProps({userId: 8})
    await settle()
    expect(mockNetworkError).toHaveBeenCalledTimes(2)
  })
})
