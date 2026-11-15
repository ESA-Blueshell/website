import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BallotList from "@/domains/vote/island/BallotList.vue"
import ResultList from "@/domains/vote/island/ResultList.vue"
import {aGame, aPick, aPlacing, aTrack} from "../fixtures"

describe("the ballot list", () => {
  const bare = aGame(20, "No cover", null)
  const picks = [aPick(aTrack(1)), {game: bare, track: null}]

  it("ranks the picks with what each is worth, a stored cover where the game has one, and a track where picked", () => {
    const wrapper = mount(BallotList, {props: {picks, points: [12, 10]}})

    const rows = wrapper.findAll("[data-testid=vote-pick]")
    expect(rows.map(row => row.text())).toEqual([expect.stringContaining("12pts"), expect.stringContaining("10pts")])
    expect(rows[0]!.find("img").attributes("src")).toMatch(/\/api\/files\/public\/vote-covers\/10\.webp$/)
    expect(rows[0]!.find(".pick__track").text()).toBe("Track 1")
    expect(rows[1]!.find("img").exists()).toBe(false)
    expect(rows[1]!.find(".pick__track").exists()).toBe(false)
  })

  it("asks to move or drop a pick, and cannot move the first up or the last down", async () => {
    const wrapper = mount(BallotList, {props: {picks, points: [12, 10]}})

    expect(wrapper.get("[data-testid=vote-pick-up-0]").attributes("disabled")).toBeDefined()
    expect(wrapper.get("[data-testid=vote-pick-down-1]").attributes("disabled")).toBeDefined()
    await wrapper.get("[data-testid=vote-pick-up-1]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-down-0]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-remove-1]").trigger("click")

    expect(wrapper.emitted("move")).toEqual([[1, -1], [0, 1]])
    expect(wrapper.emitted("remove")).toEqual([[1]])
  })
})

describe("the result list", () => {
  const youtube = aTrack(1)
  const spotify = aTrack(2, aGame(20, "No cover", null), "SPOTIFY")

  it("says there is no top when nobody voted", () => {
    expect(mount(ResultList, {props: {placings: []}}).get("[data-testid=vote-results-empty]").exists()).toBe(true)
  })

  it("lists each game's place, stored cover, track and points, the podium marked, and says players load on Play", () => {
    const wrapper = mount(ResultList, {props: {placings: [aPlacing(1, youtube.game, youtube, 40, 4), aPlacing(4, spotify.game, spotify, 8, 1)]}})

    expect(wrapper.get("[data-testid=vote-results-players]").text()).toContain("until you press Play")
    const rows = wrapper.findAll("[data-testid=vote-result]")
    expect(rows[0]!.classes()).toContain("result--podium")
    expect(rows[1]!.classes()).not.toContain("result--podium")
    expect(rows[0]!.text()).toContain("40pts")
    expect(rows[0]!.get("a").attributes("href")).toBe(youtube.url)
    expect(rows[0]!.get("img").attributes("src")).toMatch(/\/api\/files\/public\/vote-covers\/10\.webp$/)
    expect(rows[1]!.find("img").exists()).toBe(false)
  })

  it("lists a games-only top without tracks, players or the note about them", () => {
    const wrapper = mount(ResultList, {props: {placings: [aPlacing(1, aGame(3), null)]}})

    expect(wrapper.find("[data-testid=vote-results-players]").exists()).toBe(false)
    expect(wrapper.find("a").exists()).toBe(false)
    expect(wrapper.find("[data-testid=vote-play-3]").exists()).toBe(false)
  })

  it("opens one player at a time, saying which service it plays from, and closes it again", async () => {
    const wrapper = mount(ResultList, {props: {placings: [aPlacing(1, youtube.game, youtube), aPlacing(2, spotify.game, spotify)]}})

    expect(wrapper.get("[data-testid=vote-play-10]").text()).toBe("Play on YouTube")
    expect(wrapper.get("[data-testid=vote-play-20]").text()).toBe("Play on Spotify")
    await wrapper.get("[data-testid=vote-play-10]").trigger("click")
    expect(wrapper.get("[data-testid=vote-player]").attributes("src")).toContain("youtube-nocookie.com/embed/video1")
    expect(wrapper.get("[data-testid=vote-play-10]").text()).toBe("Close")

    await wrapper.get("[data-testid=vote-play-20]").trigger("click")
    expect(wrapper.findAll("[data-testid=vote-player]")).toHaveLength(1)
    expect(wrapper.get("[data-testid=vote-player]").classes()).toContain("result__player--spotify")

    await wrapper.get("[data-testid=vote-play-20]").trigger("click")
    expect(wrapper.find("[data-testid=vote-player]").exists()).toBe(false)
  })
})
