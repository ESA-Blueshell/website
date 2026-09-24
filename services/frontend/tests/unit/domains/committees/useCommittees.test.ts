import {beforeEach, describe, expect, it, vi} from "vitest"
import {cellOf, driftItemOf, forgetCommittees, initialsOf, openingLineOf, reelItemOf, useCommittees} from "@/domains/committees"

const findCommittees = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: () => findCommittees(),
}))

const committee = (id: number, name: string, over: Record<string, unknown> = {}) => ({
  id, name, slug: name.toLowerCase(), description: `${name} runs things.`, listed: true, archived: false, banner: null, gameCodes: [],
  version: 0, createdAt: "", updatedAt: "", ...over,
})

beforeEach(() => {
  forgetCommittees()
  findCommittees.mockReset()
})

describe("the committees", () => {
  it("reads them once, leaves the unlisted ones out, and splits the archived ones off", async () => {
    findCommittees.mockResolvedValue({data: [committee(1, "LanCie"), committee(2, "Board", {listed: false}), committee(3, "OldCie", {archived: true})]})
    const {committees, listed, live, archived, ready} = useCommittees()
    await ready
    useCommittees()

    expect(findCommittees).toHaveBeenCalledTimes(1)
    expect(committees.value).toHaveLength(3)
    expect(listed.value.map(one => one.id)).toEqual([1, 3])
    expect(live.value.map(one => one.id)).toEqual([1])
    expect(archived.value.map(one => one.id)).toEqual([3])
  })

  it("asks again when told to", async () => {
    findCommittees.mockResolvedValueOnce({data: [committee(1, "LanCie")]}).mockResolvedValueOnce({data: []})
    const {committees, ready, refresh} = useCommittees()
    await ready

    await refresh()

    expect(committees.value).toEqual([])
  })
})

describe("a committee as the pages draw it", () => {
  const banner = {url: "/b.webp", path: "b.webp", width: 1600, height: 900, renditions: [{url: "/b-640.webp", width: 640}]}
  const lan = committee(1, "LanCie", {banner, gameCodes: ["CS2"], description: ":tada: **LanCie** runs the LANs.\n\nMore below."})

  it("leads to its page, carries its banner, its name on the rail and its games as chips", () => {
    expect(reelItemOf(lan, codes => codes.map(code => `Game ${code}`))).toEqual({
      id: 1,
      title: "LanCie",
      href: "/committees/lancie",
      accent: "var(--color-brand)",
      banner: "/b.webp",
      srcset: "/b-640.webp 640w, /b.webp 1600w",
      initials: "L",
      railLabel: "LanCie",
      chips: ["Game CS2"],
    })
    expect(reelItemOf(committee(2, "One Off")).chips).toEqual([])
  })

  it("says its opening line under the drift tile and the cell, archived ones tagged", () => {
    expect(driftItemOf(lan).sub).toBe("LanCie runs the LANs.")
    expect(cellOf(committee(3, "OldCie", {archived: true}))).toMatchObject({sub: "OldCie runs things.", archived: true, chips: [], banner: null})
  })

  it("cuts a long opening line at a word, and makes plate letters from the first two words", () => {
    expect(openingLineOf("one two three four", 12)).toBe("one two...")
    expect(openingLineOf("one, two three", 5)).toBe("one...")
    expect(openingLineOf("")).toBe("")
    expect(initialsOf("One-Of-Committee (Member's Iniative)")).toBe("OM")
  })
})
