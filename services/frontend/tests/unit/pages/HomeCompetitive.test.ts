import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import GamesWePlay from "@/components/base/GamesWePlay.vue"
import Home from "@/pages/Home.vue"
import router from "@/plugins/router"
import {forgetGames} from "@/domains/esports/island/useGames"
import type {GameRecord} from "@/domains/esports/adapters/esports"

/**
 * The home page's competitive block, which reads the game records rather than naming its own.
 *
 * Five titles used to be literals here with bundled art and hardcoded links, so a game the
 * board renamed or re-addressed through the dialogs did not follow it to the busiest page on the
 * site. These assert that it does now, and that a game nobody currently fields is not listed.
 */
const mockLoadGames = vi.hoisted(() => vi.fn())

vi.mock("@/plugins/goto", () => ({$goto: vi.fn()}))

vi.mock("@/domains/esports/adapters/esports", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  loadGames: mockLoadGames,
}))

const record = (over: Partial<GameRecord> = {}): GameRecord => ({
  game: "VALORANT",
  name: "Valorant",
  slug: "valorant",
  current: true,
  sortIndex: 1,
  ...over,
} as GameRecord)

const picture = (url: string, widths: number[]) => ({
  url,
  path: url.replace("/files/public/", ""),
  width: widths[widths.length - 1]!,
  height: 400,
  renditions: widths.map(width => ({url: `${url.replace(".webp", "")}-${width}.webp`, width})),
})

interface Category {
  categoryName: string
  titles: Array<Record<string, unknown>>
}

const mountHome = async () => {
  const wrapper = shallowMount(Home, {
    global: {stubs: {MainBanner: true, DiscordBanner: true, SocialsBanner: true, GamesWePlay: true}},
  })
  // The records are read once and shared, so the block fills in after the read settles.
  await flushPromises()
  return wrapper
}

/**
 * The categories as the page hands them to the block that draws them.
 *
 * Read off the child's props rather than out of `wrapper.vm`: what this page does with the
 * records is give them to `GamesWePlay`, and that hand-off is the public surface (testing
 * ADR-004). A `<script setup>` binding is not exposed and reaching for it would be reflection.
 */
const categoriesOf = (wrapper: Awaited<ReturnType<typeof mountHome>>) =>
  wrapper.findComponent(GamesWePlay).props("games") as Category[]

beforeEach(() => {
  forgetGames()
  mockLoadGames.mockReset()
})

describe("the home page's competitive block", () => {
  it("names the games from their records, and addresses them from their slugs", async () => {
    mockLoadGames.mockResolvedValue([
      record({game: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2"}),
      record({game: "TRACKMANIA", name: "Trackmania", slug: "trackmania"}),
    ])

    const competitive = categoriesOf(await mountHome()).find(one => one.categoryName === "Competitive")

    expect(competitive?.titles.map(one => one.title)).toEqual(["Counter-Strike 2", "Trackmania"])
    expect(competitive?.titles.map(one => one.esportsLink))
      .toEqual(["/esports/counter-strike-2", "/esports/trackmania"])
  })

  it("follows a game re-addressed through the dialog, with no deploy", async () => {
    mockLoadGames.mockResolvedValue([record({slug: "valorant-two"})])

    const competitive = categoriesOf(await mountHome()).find(one => one.categoryName === "Competitive")

    // The link is the record's address, so changing it in the dialog changes this.
    expect(competitive?.titles[0]?.esportsLink).toBe("/esports/valorant-two")
  })

  it("leaves out a game nobody currently fields", async () => {
    mockLoadGames.mockResolvedValue([
      record(),
      record({game: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", current: false}),
    ])

    const competitive = categoriesOf(await mountHome()).find(one => one.categoryName === "Competitive")

    expect(competitive?.titles.map(one => one.title)).toEqual(["Valorant"])
  })

  it("offers each picture at the widths its record is stored at", async () => {
    mockLoadGames.mockResolvedValue([
      record({
        banner: picture("/files/public/game-banners/abc.webp", [320, 640, 1280]),
        icon: picture("/files/public/game-icons/def.webp", [128, 256, 512]),
      } as Partial<GameRecord>),
    ])

    const tile = categoriesOf(await mountHome())
      .find(one => one.categoryName === "Competitive")?.titles[0]

    expect(tile?.bg).toBe("/files/public/game-banners/abc.webp")
    expect(tile?.bgSrcset).toContain("/files/public/game-banners/abc-320.webp 320w")
    expect(tile?.bgSrcset).toContain("1280w")
    expect(tile?.iconSrcset).toContain("/files/public/game-icons/def-128.webp 128w")
  })

  it("draws a game with no art rather than a broken tile", async () => {
    mockLoadGames.mockResolvedValue([record({banner: null, icon: null})])

    const tile = categoriesOf(await mountHome())
      .find(one => one.categoryName === "Competitive")?.titles[0]

    expect(tile?.bg).toBe("")
    expect(tile?.bgSrcset).toBeUndefined()
  })

  it("drops the whole block when the records cannot be read, rather than showing a bare heading", async () => {
    mockLoadGames.mockResolvedValue([])

    const names = categoriesOf(await mountHome()).map(one => one.categoryName)

    expect(names).not.toContain("Competitive")
    // The community block is written down here and is unaffected by the api being unreachable.
    expect(names).toContain("Community")
  })

  it("keeps the community block naming its own games and art", async () => {
    mockLoadGames.mockResolvedValue([record()])

    const community = categoriesOf(await mountHome()).find(one => one.categoryName === "Community")

    expect(community?.titles.map(one => one.title)).toContain("Minecraft")
    // Bundled, so no widths: these games have no record to be stored at several sizes.
    expect(community?.titles.every(one => one.bgSrcset === undefined)).toBe(true)
  })

  it("addresses every competitive tile at a route that exists", async () => {
    mockLoadGames.mockResolvedValue([
      record(),
      record({game: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2"}),
    ])

    const competitive = categoriesOf(await mountHome()).find(one => one.categoryName === "Competitive")

    for (const tile of competitive?.titles ?? []) {
      expect(router.resolve(tile.esportsLink as string).matched.length).toBeGreaterThan(0)
    }
  })
})
