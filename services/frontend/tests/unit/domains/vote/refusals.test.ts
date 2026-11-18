import {describe, expect, it} from "vitest"
import {reasonFor} from "@/domains/vote"

describe("vote refusals", () => {
  const sentence = (body: Record<string, unknown>) => reasonFor(body, "fallback")

  it("says where the vote stands when it does not allow a write", () => {
    expect(sentence({code: "WrongVoteState", state: "UPCOMING"})).toBe("The vote has not opened yet, so that cannot be done now.")
    expect(sentence({code: "WrongVoteState", state: "VOTING"})).toBe("The vote is open, so that cannot be done now.")
    expect(sentence({code: "WrongVoteState", state: "CLOSED"})).toBe("The vote has closed, so that cannot be done now.")
    expect(sentence({code: "WrongVoteState", state: "REVEALED"})).toBe("The vote has been revealed, so that cannot be done now.")
    expect(sentence({code: "WrongVoteState"})).toBe("The vote is not open, so that cannot be done now.")
  })

  it("says each other code in the member's words", () => {
    expect(sentence({code: "BallotSize", max: 5})).toBe("A ballot holds one to 5 picks.")
    expect(sentence({code: "BallotSize"})).toBe("A ballot holds too few or too many picks.")
    expect(sentence({code: "GameTrackLimit", max: 30})).toBe("That game already has 30 tracks. Pick one of them.")
    expect(sentence({code: "GameTrackLimit"})).toBe("That game has all the tracks it can take. Pick one of them.")
    expect(sentence({code: "GamePickedTwice"})).toContain("once")
    expect(sentence({code: "PickNeedsTrack"})).toContain("track for each game")
    expect(sentence({code: "PickTakesNoTrack"})).toBe("This vote picks games, not tracks.")
    expect(sentence({code: "GameNotEligible", year: 2026})).toBe("Only games first released in 2026 can be picked.")
    expect(sentence({code: "GameNotEligible"})).toContain("the vote's year")
    expect(sentence({code: "UnsupportedTrackLink"})).toBe("Paste a link to a YouTube video or a Spotify track.")
    expect(sentence({code: "TrackLinkUnreadable"})).toContain("private or removed")
    expect(sentence({code: "TrackMergeInvalid"})).toContain("same game")
    expect(sentence({code: "GameSearchUnavailable"})).toContain("unavailable")
    expect(sentence({code: "AnnualVoteExists"})).toBe("That year already has this vote.")
    expect(sentence({code: "VoteDatesOutOfOrder"})).toContain("opens before it closes")
    expect(sentence({code: "VoteKindNotOffered"})).toBe("That vote is not running yet.")
  })
})
