import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import BallotList from "@/domains/vote/island/BallotList.vue"
import PlaceEditor from "@/domains/vote/island/PlaceEditor.vue"
import ResultList from "@/domains/vote/island/ResultList.vue"
import {aGame, aPick, aPlacing, aTrack, aVote} from "../fixtures"

describe("the ballot list", () => {
  const bare = aGame(20, "No cover", null)
  const picks = [aPick(aTrack(1)), {candidate: bare, track: null}]
  const vote = aVote("VOTING", {points: [12, 10, 8, 6]})
  const listed = () => mount(BallotList, {props: {picks, vote}, attachTo: document.body})

  // Lays the rows out as a browser would, 100px apart and 90px tall, for a list at the top of the page.
  const laidOut = (wrapper: ReturnType<typeof listed>) => {
    wrapper.findAll("[data-pick]").forEach((row, index) => {
      const element = row.element as HTMLElement
      Object.defineProperty(element, "offsetTop", {value: index * 100, configurable: true})
      Object.defineProperty(element, "offsetHeight", {value: 90, configurable: true})
      element.getBoundingClientRect = () => ({top: element.offsetTop}) as DOMRect
    })
  }

  beforeEach(() => {
    Element.prototype.setPointerCapture = vi.fn()
  })

  it("ranks the picks with what each is worth, a stored cover where the game has one, and a track where picked", () => {
    const wrapper = listed()

    const rows = wrapper.findAll("[data-testid=vote-pick]")
    expect(rows.map(row => row.text())).toEqual([expect.stringContaining("12pts"), expect.stringContaining("10pts")])
    expect(rows[0]!.find("img").attributes("src")).toMatch(/\/api\/files\/public\/vote-covers\/10\.webp$/)
    expect(rows[0]!.find(".pick-row__track").text()).toBe("Track 1")
    expect(rows[1]!.find("img").exists()).toBe(false)
    expect(rows[1]!.find(".pick-row__track").exists()).toBe(false)
  })

  it("shows every free place with its points, each filled in where it stands", async () => {
    const wrapper = listed()

    const free = wrapper.findAll("[data-testid=vote-free]")
    expect(free.map(row => row.find(".pick-row__points").text())).toEqual(["8pts", "6pts"])
    expect(free.map(row => row.findComponent(PlaceEditor).exists())).toEqual([true, true])

    const editor = free[0]!.findComponent(PlaceEditor)
    editor.vm.$emit("pick", picks[0])
    editor.vm.$emit("problem", "No.")
    expect(wrapper.emitted("pick")).toEqual([[picks[0]]])
    expect(wrapper.emitted("problem")).toEqual([["No."]])
  })

  it("lets a member rename only the songs they may, and hands up a rename and a song added in a place", async () => {
    const wrapper = mount(BallotList, {props: {picks, vote, renamable: [1]}})

    expect(wrapper.get("[data-testid=vote-pick-title-0]").element.tagName).toBe("BUTTON")
    await wrapper.get("[data-testid=vote-pick-title-0]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-title-0-field]").setValue("Resurrections")
    await wrapper.get("[data-testid=vote-pick-title-0-field]").trigger("keydown", {key: "Enter"})
    expect(wrapper.emitted("rename")).toEqual([[1, "Resurrections"]])

    wrapper.findComponent(PlaceEditor).vm.$emit("added", 5)
    expect(wrapper.emitted("added")).toEqual([[5]])
    expect(mount(BallotList, {props: {picks, vote}}).get("[data-testid=vote-pick-title-0]").element.tagName).toBe("SPAN")
  })

  it("shows a cast top as it is, without free places or anything to move", () => {
    const wrapper = mount(BallotList, {props: {picks, vote, readonly: true}})

    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(2)
    expect(wrapper.find("[data-testid=vote-free]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=vote-pick-grip-0]").exists()).toBe(false)
  })

  it("asks to move or drop a pick, and cannot move the first up or the last down", async () => {
    const wrapper = listed()

    expect(wrapper.get("[data-testid=vote-pick-up-0]").attributes("disabled")).toBeDefined()
    expect(wrapper.get("[data-testid=vote-pick-down-1]").attributes("disabled")).toBeDefined()
    await wrapper.get("[data-testid=vote-pick-up-1]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-down-0]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-remove-1]").trigger("click")

    expect(wrapper.emitted("move")).toEqual([[1, -1], [0, 1]])
    expect(wrapper.emitted("remove")).toEqual([[1]])
  })

  it("steps a pick with the arrow keys on its handle, within the list", async () => {
    const wrapper = listed()

    await wrapper.get("[data-testid=vote-pick-grip-1]").trigger("keydown", {key: "ArrowUp"})
    await wrapper.get("[data-testid=vote-pick-grip-0]").trigger("keydown", {key: "ArrowDown"})
    await wrapper.get("[data-testid=vote-pick-grip-0]").trigger("keydown", {key: "ArrowUp"})
    await wrapper.get("[data-testid=vote-pick-grip-1]").trigger("keydown", {key: "ArrowDown"})
    await wrapper.get("[data-testid=vote-pick-grip-0]").trigger("keydown", {key: "Enter"})

    expect(wrapper.emitted("move")).toEqual([[1, -1], [0, 1]])
    wrapper.unmount()
  })

  it("drags a pick by its handle past the middle of the next, up or down, lifting it under the pointer", async () => {
    const wrapper = listed()
    laidOut(wrapper)
    // jsdom has no PointerEvent, so a mouse event of the pointer's name stands in for one.
    const grip = (index: number) => ({
      trigger: async (type: string, init: {button?: number, clientY?: number} = {}) => {
        wrapper.get(`[data-testid=vote-pick-grip-${index}]`).element.dispatchEvent(new MouseEvent(type, {bubbles: true, ...init}))
        await flushPromises()
      },
    })

    await grip(1).trigger("pointerdown", {button: 2, clientY: 150})
    await grip(1).trigger("pointermove", {clientY: 40})
    expect(wrapper.emitted("reorder")).toBeUndefined()

    await grip(1).trigger("pointerdown", {button: 0, clientY: 150})
    expect(wrapper.findAll("[data-pick]")[1]!.classes()).toContain("ballot__place--held")
    await grip(1).trigger("pointermove", {clientY: 120})
    expect(wrapper.findAll("[data-pick]")[1]!.attributes("style")).toContain("translate: 0 -30px")
    await grip(1).trigger("pointermove", {clientY: 40})
    expect(wrapper.emitted("reorder")).toEqual([[1, 0]])
    await grip(1).trigger("pointerup")
    expect(wrapper.find(".ballot__place--held").exists()).toBe(false)

    await grip(0).trigger("pointerdown", {button: 0, clientY: 10})
    await grip(0).trigger("pointermove", {clientY: 200})
    expect(wrapper.emitted("reorder")).toEqual([[1, 0], [0, 1]])
    await grip(0).trigger("pointercancel")
    await grip(0).trigger("pointermove", {clientY: 10})
    expect(wrapper.emitted("reorder")).toHaveLength(2)
    wrapper.unmount()
  })
})

describe("the result list", () => {
  const youtube = aTrack(1)
  const spotify = aTrack(2, aGame(20, "No cover", null), "SPOTIFY")

  it("says there is no top when nobody voted", () => {
    expect(mount(ResultList, {props: {placings: []}}).get("[data-testid=vote-results-empty]").exists()).toBe(true)
  })

  it("lists each game's place in its own colour, stored cover, track and points, and says players load on Play", () => {
    const wrapper = mount(ResultList, {
      props: {placings: [aPlacing(1, youtube.candidate, youtube, 40, 4), aPlacing(7, spotify.candidate, spotify, 8, 1)]},
    })

    expect(wrapper.get("[data-testid=vote-results-players]").text()).toContain("until you press Play")
    const rows = wrapper.findAll("[data-testid=vote-result]")
    expect(rows[0]!.get(".pick-row").attributes("style")).toContain("--rank: var(--color-warning)")
    expect(rows[1]!.get(".pick-row").attributes("style")).toContain("--rank: var(--color-ash)")
    expect(rows[1]!.get(".pick-row").classes()).toContain("pick-row--far")
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
    const wrapper = mount(ResultList, {props: {placings: [aPlacing(1, youtube.candidate, youtube), aPlacing(2, spotify.candidate, spotify)]}})

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
