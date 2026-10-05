import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import GameList from "@/pages/management/GameList.vue"
import {forgetCasualGames} from "@/domains/games"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findCasualGames: vi.fn(), listCataloguedChannels: vi.fn(), createGameChannel: vi.fn(), updateCasualGame: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const channel = (id: string, name: string) => ({id, guildId: "99", name})
const game = (code: string, name: string, fields: Record<string, unknown> = {}) => ({
  code, name, slug: name.toLowerCase(), archived: false, inCompetition: false, sortIndex: 0, channels: [], esportsChannels: [], ...fields,
})

describe("the games in Management", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(GameList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    forgetCasualGames()
    api.findCasualGames.mockResolvedValue({status: 200, data: [
      game("VALO", "Valorant", {inCompetition: true, channels: [channel("1", "bs-valo")], esportsChannels: [channel("2", "blueshell-valorant")]}),
      game("CHESS", "Chess"),
      game("OLD", "Oldgame", {archived: true}),
    ]})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [
      {id: "1", name: "bs-valo", kind: "TEXT", private: false, roleIds: [], access: {kept: {everyone: "READ", members: "WRITE"}, actual: {everyone: "WRITE", members: "WRITE"}, differs: true}},
      {id: "2", name: "blueshell-valorant", kind: "TEXT", private: true, roleIds: []},
    ]})
  })

  afterEach(() => unmountAll(wrappers, "GameListPage"))

  it("lists each game with its channels, whether it is in competition and whether Discord differs", async () => {
    const wrapper = await mount()

    const valorant = wrapper.get('[data-testid="game-row-VALO"]')
    expect(valorant.text()).toContain("#bs-valo, #blueshell-valorant")
    expect(valorant.text()).toContain("Esports")
    expect(wrapper.get('[data-testid="game-differs-VALO"]').text()).toBe("Differs on Discord")
    expect(valorant.get("a").attributes("to")).toBe("/management/games/valorant")
    expect(wrapper.get('[data-testid="game-row-CHESS"]').text()).toContain("No channel")
    expect(wrapper.get('[data-testid="game-row-CHESS"]').text()).toContain("Casual")
    expect(wrapper.get('[data-testid="game-differs-CHESS"]').text()).toBe("In step")
    expect(wrapper.get('[data-testid="game-differs-OLD"]').text()).toBe("Archived")
    expect(wrapper.findAll('[data-testid^="game-row-"]').at(-1)!.attributes("data-testid")).toBe("game-row-OLD")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("1 archived")
  })

  it("draws each game as a row on a phone", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="game-row-CHESS"]').text()).toContain("Casual · No channel")
    expect(wrapper.get('[data-testid="game-differs-CHESS"]').text()).toBe("In step")
  })

  it("narrows by a name, code or channel, and says when nothing matches", async () => {
    const wrapper = await mount()

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "blueshell")
    await settle()
    expect(wrapper.findAll('[data-testid^="game-row-"]').map((one) => one.attributes("data-testid"))).toEqual(["game-row-VALO"])
    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "zzz")
    await settle()
    expect(wrapper.get('[data-testid="game-list-empty"]').text()).toBe("No game matches.")
  })

  it("gives the ticked games without a channel one in the Games category, and leaves out the rest", async () => {
    api.createGameChannel.mockResolvedValue({status: 200, data: {id: "77", guildId: "99", name: "chess", url: "https://discord.test/77"}})
    api.updateCasualGame.mockResolvedValue({status: 200, data: game("CHESS", "Chess", {channels: [channel("77", "chess")]})})
    const wrapper = await mount()
    const bulk = () => wrapper.findComponent({name: "BulkAdd"})

    expect(wrapper.get('[data-testid="game-list-selection"]').text()).toContain("0 selected")
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("selectAll")
    await settle()
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("clearSelection")
    await settle()
    for (const code of ["VALO", "CHESS", "OLD"]) await wrapper.get(`[data-testid="game-check-${code}"]`).setValue(true)
    await wrapper.get('[data-testid="game-add-channels"]').trigger("click")
    await settle()

    expect(bulk().props("open")).toBe(true)
    expect(bulk().props("items").map((one: {name: string; note: string}) => [one.name, one.note])).toEqual([["Chess", "#chess under Games"]])
    expect(bulk().props("skipped")).toEqual([{name: "Valorant", why: "Has a channel already"}, {name: "Oldgame", why: "Archived"}])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.createGameChannel).toHaveBeenCalledWith({body: {name: "chess", category: "GAMES"}})
    // The save says the whole record again, with the one channel added.
    expect(api.updateCasualGame).toHaveBeenCalledWith({path: {game: "CHESS"}, body: expect.objectContaining({name: "Chess", slug: "chess", channels: [channel("77", "chess")], esportsChannels: [], sortIndex: 0})})

    api.updateCasualGame.mockResolvedValue({status: 409, error: {message: "Changed meanwhile."}})
    expect((await bulk().props("run")(bulk().props("items")[0])).ok).toBe(false)
    api.createGameChannel.mockResolvedValue({status: 503, error: {code: "DiscordUnreachable"}})
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: false, reason: "Discord cannot be reached now; try again in a moment."})

    api.findCasualGames.mockClear()
    bulk().vm.$emit("done")
    await settle()
    expect(api.findCasualGames).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="game-list-selection"]').text()).toContain("0 selected")
    bulk().vm.$emit("update:open", false)
    await settle()
    expect(bulk().props("open")).toBe(false)
  })
})
