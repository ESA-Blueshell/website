import {describe, expect, it} from "vitest"
import {accountFor, covers, managementFor, sectionsFor} from "@/components/common/nav"

const GAMES = [{name: "Valorant", slug: "valorant"}, {name: "Trackmania", slug: "trackmania"}]

describe("the bar's own declaration", () => {
  it("offers a game's page for every game the association fields", () => {
    const esports = sectionsFor(GAMES).find(section => section.label === "Esports")

    expect(esports?.entries?.map(entry => entry.to)).toEqual([
      "/esports/competitive-scene",
      "/esports/valorant",
      "/esports/trackmania",
    ])
  })

  it("marks the section a reader is under, from a page below it", () => {
    const [home, , association] = sectionsFor([])

    expect(covers("/esports/valorant", sectionsFor([])[4])).toBe(true)
    expect(covers("/board", association)).toBe(true)
    expect(covers("/board", home)).toBe(false)
  })

  it("marks home on home alone", () => {
    const [home] = sectionsFor([])

    expect(covers("/", home)).toBe(true)
    expect(covers("/membership", home)).toBe(false)
  })

  // The gates are the controllers': a board runs the records, an admin the machinery, and the
  // outbox answers to both.
  it("offers management by what the reader may reach", () => {
    const board = managementFor({loggedIn: true, board: true, admin: false}).map(entry => entry.to)
    const admin = managementFor({loggedIn: true, board: false, admin: true}).map(entry => entry.to)
    const member = managementFor({loggedIn: true, board: false, admin: false})

    expect(board).toContain("/user-manager")
    expect(board).not.toContain("/management/jobs")
    expect(board).toContain("/management/emails")
    expect(admin).toContain("/management/jobs")
    expect(admin).not.toContain("/user-manager")
    expect(admin).toContain("/management/emails")
    expect(member).toEqual([])
  })

  // A reader with no address has nothing to edit, so the entry is not offered at all.
  it("offers an address only where the reader has one", () => {
    const withAddress = accountFor({loggedIn: true, board: false, admin: false, addressId: 12})
    const without = accountFor({loggedIn: true, board: false, admin: false, addressId: null})

    expect(withAddress.map(entry => entry.to)).toEqual(["/account", "/account/security", "/account/games", "/account/addresses/12"])
    expect(without.map(entry => entry.to)).toEqual(["/account", "/account/security", "/account/games"])
  })
})
