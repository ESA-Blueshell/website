import {describe, expect, it} from "vitest"
import {BOARD_CALL} from "@/domains/boards/island/boardCall"

describe("BOARD_CALL", () => {
  it("offers the two ways of asking, and nothing in front of them", () => {
    expect(BOARD_CALL.actions.map(action => action.href)).toEqual([
      "https://discord.gg/23YMFQy",
      "mailto:board@blueshell.utwente.nl",
    ])
  })

  it("leads with one action, so the eye is sent to a single place", () => {
    expect(BOARD_CALL.actions.filter(action => action.tone === "solid")).toHaveLength(1)
  })

  it("opens a new tab for what leaves the site and for nothing else", () => {
    // [away] is what CallBand turns into target=_blank. A mail address opens the mail client
    // rather than navigating, so a tab opened for it would be left blank.
    const away = BOARD_CALL.actions.filter(action => action.away).map(action => action.href)

    expect(away).toEqual(["https://discord.gg/23YMFQy"])
  })

  it("names every action apart, since the tests reach them by testid", () => {
    const testids = BOARD_CALL.actions.map(action => action.testid)

    expect(testids).toEqual(["board-join-discord", "board-join-mail"])
    expect(BOARD_CALL.testid).toBe("board-join")
  })

  it("says the window applications open in, and that Discord is answered outside it", () => {
    // Somebody reading in October should not take this for a door that is shut.
    expect(BOARD_CALL.body).toContain("March to May")
    expect(BOARD_CALL.body).toContain("any time of year")
  })
})
