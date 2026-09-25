import {describe, expect, it} from "vitest"
import {accountFor, covers, managementFor, sectionsFor} from "@/components/common/nav"

const GAMES = [{name: "Valorant", slug: "valorant"}, {name: "Trackmania", slug: "trackmania"}]

describe("the bar's own declaration", () => {
  it("offers a game's page for every game the association fields", () => {
    const esports = sectionsFor(GAMES).find(section => section.label === "Competition")

    expect(esports?.entries?.map(entry => entry.to)).toEqual([
      "/competition",
      "/competition/valorant",
      "/competition/trackmania",
    ])
  })

  it("offers the committees their own section: the index, then every committee running now", () => {
    const sections = sectionsFor([], [{name: "LanCie", slug: "lancie"}, {name: "NintenCo", slug: "nintenco"}])
    const committees = sections.find(section => section.label === "Committees")
    const association = sections.find(section => section.label === "Association")

    expect(committees?.entries).toEqual([
      {label: "All committees", to: "/committees"},
      {label: "LanCie", to: "/committees/lancie"},
      {label: "NintenCo", to: "/committees/nintenco"},
    ])
    expect(association?.entries?.map(entry => entry.to)).not.toContain("/committees")
    expect(covers("/committees/lancie", committees!)).toBe(true)
    expect(covers("/committees/lancie", association!)).toBe(false)
  })

  it("marks the section a reader is under, from a page below it", () => {
    const sections = sectionsFor([])
    const named = (label: string) => sections.find(section => section.label === label)!

    expect(covers("/competition/valorant", named("Competition"))).toBe(true)
    expect(covers("/casual/chess", named("Casual"))).toBe(true)
    expect(covers("/board", named("Association"))).toBe(true)
    expect(covers("/board", named("Home"))).toBe(false)
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
