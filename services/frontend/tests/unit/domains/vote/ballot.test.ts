import {describe, expect, it} from "vitest"
import {embedUrl, moved, pointsAt, samePicks, without, withPick} from "@/domains/vote"
import {aGame, aPick, aTrack} from "./fixtures"

describe("a vote ballot", () => {
  const hollow = aGame(1, "Hollow Knight")
  const one = aPick(aTrack(1, hollow))
  const two = aPick(aTrack(2))
  const three = aPick(aTrack(3))

  it("is worth what the kind's points say at each position, and nothing past the last", () => {
    expect([0, 1, 2].map(index => pointsAt([5, 3], index))).toEqual([5, 3, 0])
  })

  it("adds a pick at the end, or in place of a pick of the same game", () => {
    expect(withPick([two], one, 5)).toEqual([two, one])
    const otherHollowTrack = aPick(aTrack(9, hollow))
    expect(withPick([one, two], otherHollowTrack, 5)).toEqual([otherHollowTrack, two])
  })

  it("takes no new game once full, but still swaps a game's track", () => {
    const full = [two, three, one]
    expect(withPick(full, aPick(aTrack(4)), 3)).toBe(full)
    expect(withPick(full, aPick(aTrack(9, hollow)), 3)[2]!.track!.id).toBe(9)
  })

  it("moves a pick one place up or down, and not past either end", () => {
    expect(moved([one, two, three], 2, -1)).toEqual([one, three, two])
    expect(moved([one, two, three], 0, 1)).toEqual([two, one, three])
    const picks = [one, two]
    expect(moved(picks, 0, -1)).toBe(picks)
    expect(moved(picks, 1, 1)).toBe(picks)
  })

  it("drops a pick", () => {
    expect(without([one, two, three], 1)).toEqual([one, three])
  })

  it("is the same ballot only with the same games and tracks in the same order", () => {
    expect(samePicks([one, two], [aPick(aTrack(1, hollow)), aPick(aTrack(2))])).toBe(true)
    expect(samePicks([one, two], [two, one])).toBe(false)
    expect(samePicks([one], [one, two])).toBe(false)
    expect(samePicks([one], [aPick(aTrack(9, hollow))])).toBe(false)
    expect(samePicks([{candidate: hollow, track: null}], [{candidate: hollow}])).toBe(true)
  })

  it("plays a YouTube track in the privacy-enhanced player and a Spotify track in Spotify's", () => {
    expect(embedUrl({source: "YOUTUBE", externalId: "dQw4w9WgXcQ"})).toBe("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")
    expect(embedUrl({source: "SPOTIFY", externalId: "4cOd"})).toBe("https://open.spotify.com/embed/track/4cOd")
  })
})
