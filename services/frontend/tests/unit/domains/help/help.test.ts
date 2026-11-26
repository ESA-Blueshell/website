import {describe, expect, it} from "vitest"
import {audiencesIn, helpAbout, helpFor, searchHelp, type HelpSection} from "@/domains/help"

const sections: HelpSection[] = [
  {
    id: "events",
    title: "Events",
    topics: [
      {id: "events-sign-up", title: "Sign up for an event", audience: "everyone", body: ["Press Sign up on the event."]},
      {id: "events-add", title: "Add an event", audience: "committee", body: ["Press Add an event."], steps: ["Fill in the poster."]},
      {id: "events-approve", title: "Approve an event", audience: "board", body: ["Press Awaiting approval."]},
      {id: "events-fees", title: "Set an event's fee", audience: "treasurer", body: ["Fill in the price."]},
    ],
  },
  {
    id: "site",
    title: "Running the site",
    topics: [
      {id: "site-jobs", title: "Background jobs", audience: "admin", body: ["Retry runs a failed job again."], links: [{label: "Manage jobs", to: "/management/jobs"}]},
    ],
  },
  {
    id: "contributions",
    title: "Contributions",
    topics: [{id: "contributions-pay", title: "How you pay", audience: "member", body: ["The board emails you."]}],
  },
]

const nobody = {member: false, committee: false, board: false, treasurer: false, admin: false}
const ids = (found: HelpSection[]) => found.flatMap(section => section.topics.map(topic => topic.id))

describe("the help a reader gets", () => {
  it("is what everybody may read, for somebody with no role, and leaves out a section with nothing for them", () => {
    const found = helpFor(sections, nobody)

    expect(ids(found)).toEqual(["events-sign-up"])
    expect(found.map(section => section.id)).toEqual(["events"])
  })

  it("adds what each role may do, one role at a time", () => {
    expect(ids(helpFor(sections, {...nobody, member: true}))).toEqual(["events-sign-up", "contributions-pay"])
    expect(ids(helpFor(sections, {...nobody, committee: true}))).toEqual(["events-sign-up", "events-add"])
    expect(ids(helpFor(sections, {...nobody, board: true}))).toEqual(["events-sign-up", "events-approve"])
    expect(ids(helpFor(sections, {...nobody, treasurer: true}))).toEqual(["events-sign-up", "events-fees"])
    expect(ids(helpFor(sections, {...nobody, admin: true}))).toEqual(["events-sign-up", "site-jobs"])
  })
})

describe("the help about one role", () => {
  it("names the roles that have a topic, in the order a person grows into them", () => {
    expect(audiencesIn(sections)).toEqual(["everyone", "member", "committee", "board", "treasurer", "admin"])
    expect(audiencesIn(helpFor(sections, {...nobody, member: true}))).toEqual(["everyone", "member"])
  })

  it("keeps only that role's own topics, and everything when no role is picked", () => {
    expect(ids(helpAbout(sections, "everyone"))).toEqual(["events-sign-up"])
    expect(ids(helpAbout(sections, "treasurer"))).toEqual(["events-fees"])
    expect(helpAbout(sections, null)).toEqual(sections)
  })
})

describe("searching the help", () => {
  it("keeps everything for an empty search", () => {
    expect(searchHelp(sections, "  ")).toEqual(sections)
  })

  it("finds a topic by a word in its title, its text, its steps or a page it links to, whatever the capitals", () => {
    expect(ids(searchHelp(sections, "APPROVE"))).toEqual(["events-approve"])
    expect(ids(searchHelp(sections, "failed"))).toEqual(["site-jobs"])
    expect(ids(searchHelp(sections, "poster"))).toEqual(["events-add"])
    expect(ids(searchHelp(sections, "manage jobs"))).toEqual(["site-jobs"])
  })

  it("wants every word typed, in any order, and finds a whole section by its name", () => {
    expect(ids(searchHelp(sections, "event add"))).toEqual(["events-add"])
    expect(ids(searchHelp(sections, "event jobs"))).toEqual([])
    expect(ids(searchHelp(sections, "contributions"))).toEqual(["contributions-pay"])
  })

  it("leaves out a section in which nothing matches", () => {
    expect(searchHelp(sections, "retry").map(section => section.id)).toEqual(["site"])
  })
})
