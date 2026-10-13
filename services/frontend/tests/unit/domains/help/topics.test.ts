import {describe, expect, it} from "vitest"
import {HELP, helpAbout, helpFor} from "@/domains/help"
import router from "@/plugins/router"

const topics = HELP.flatMap(section => section.topics)
const words = (texts: string[]) => texts.join(" ")

describe("what the Help page says", () => {
  it("names every section and topic once", () => {
    const ids = [...HELP.map(section => section.id), ...topics.map(topic => topic.id)]

    expect(new Set(ids).size).toBe(ids.length)
  })

  it("sends every link to a page the site has", () => {
    const missing = topics
      .flatMap(topic => topic.links ?? [])
      .filter(link => router.resolve(link.to).matched.length === 0)

    expect(missing).toEqual([])
  })

  it("says something in every topic, in sentences without dashes or semicolons", () => {
    for (const topic of topics) {
      const text = words([topic.title, ...topic.body, ...(topic.steps ?? [])])

      expect(topic.body.length, topic.id).toBeGreaterThan(0)
      expect(text, topic.id).not.toMatch(/[—–;]/u)
    }
  })

  it("has the areas a member asks about, for somebody with no role", () => {
    const nobody = helpFor(HELP, {member: false, committee: false, board: false, treasurer: false, admin: false})

    expect(nobody.map(section => section.title)).toEqual([
      "Your account", "Signing in and security", "Account recovery", "Membership", "Contributions", "Events", "Esports", "Roles", "Still stuck?",
    ])
  })

  it("adds the board's and the admin's own sections for them", () => {
    const board = helpFor(HELP, {member: true, committee: true, board: true, treasurer: true, admin: false}).map(section => section.title)
    const admin = helpFor(HELP, {member: true, committee: true, board: true, treasurer: true, admin: true}).map(section => section.title)

    expect(board).toContain("Members and accounts")
    expect(board).toContain("Emails the site sends")
    expect(board).not.toContain("Running the site")
    expect(admin).toContain("Running the site")
  })

  it("says how to turn two-factor off, and gives the treasurer the contribution work", () => {
    const board = helpFor(HELP, {member: true, committee: true, board: true, treasurer: true, admin: false})
    const treasurers = helpAbout(board, "treasurer").flatMap(section => section.topics.map(topic => topic.id))

    expect(topics.find(topic => topic.id === "security-turn-off")!.steps).toContain("Press Turn off.")
    expect(treasurers).toEqual(["contributions-periods", "contributions-paid", "contributions-emails"])
  })

  it("tells the board and everybody else apart on account recovery", () => {
    const recovery = (board: boolean, admin: boolean) =>
      helpFor(HELP, {member: true, committee: false, board, treasurer: board, admin})
        .find(section => section.id === "recovery")!
        .topics.map(topic => topic.id)

    expect(recovery(false, false)).toEqual(["recovery-forgot-password", "recovery-lost-phone", "recovery-locked", "recovery-not-confirmed"])
    expect(recovery(true, false)).toContain("recovery-board-manage")
    expect(recovery(true, false)).not.toContain("recovery-admin-security")
    expect(recovery(true, true)).toContain("recovery-admin-security")
  })
})
