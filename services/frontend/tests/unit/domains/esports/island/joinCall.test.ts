import {describe, expect, it} from "vitest"
import {JOIN_CALL} from "@/domains/esports/island/joinCall"

describe("JOIN_CALL", () => {
  it("offers the three ways in, in the order that puts joining first", () => {
    expect(JOIN_CALL.actions.map(action => action.href)).toEqual([
      "/membership",
      "https://discord.gg/cauRtRaqh",
      "mailto:esports-affairs@blueshell.utwente.nl",
    ])
  })

  it("leads with becoming a member, which is the one that puts somebody on a roster", () => {
    const solid = JOIN_CALL.actions.filter(action => action.tone === "solid")

    expect(solid.map(action => action.href)).toEqual(["/membership"])
  })

  it("opens a new tab for what leaves the site and for nothing else", () => {
    // [away] is what CallBand turns into target=_blank. The membership page is the router's,
    // and a mail address opens the mail client rather than navigating.
    const away = JOIN_CALL.actions.filter(action => action.away).map(action => action.href)

    expect(away).toEqual(["https://discord.gg/cauRtRaqh"])
  })

  it("names every action apart, since the tests reach them by testid", () => {
    const testids = JOIN_CALL.actions.map(action => action.testid)

    expect(testids).toEqual(["esports-join-member", "esports-join-discord", "esports-join-mail"])
    expect(JOIN_CALL.testid).toBe("esports-join")
  })
})
