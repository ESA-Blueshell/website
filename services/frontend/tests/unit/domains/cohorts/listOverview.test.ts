import {describe, expect, it} from "vitest"
import {CohortType, driftOf, followsOf, groupsOf, missingNotice, overviewFacts, type ListedTarget, type MissingTarget} from "@/domains/cohorts"

const list = (fields: Partial<ListedTarget>): ListedTarget => ({externalId: "1", label: "List", enforced: false, ...fields})
const missing = (fields: Partial<MissingTarget> = {}): MissingTarget => ({
  targetId: 3, cohortId: 103, cohortLabel: "Paid 2026-2027", cohortType: CohortType.PERIOD_PAYERS, folder: "Contribution paid",
  memberCount: 142, creating: false, ...fields,
})

describe("the Brevo page's reading", () => {
  it("names a list's drift from its newest reconcile", () => {
    expect(driftOf(list({}))).toEqual({kind: "not-compared", word: "Not compared"})
    expect(driftOf(list({targetId: 1}))).toEqual({kind: "not-compared", word: "Not reconciled yet"})
    expect(driftOf(list({targetId: 1, missing: 0, extra: 0}))).toEqual({kind: "in-step", word: "In step"})
    expect(driftOf(list({targetId: 1, missing: 2, extra: 0}))).toEqual({kind: "missing", word: "2 missing"})
    expect(driftOf(list({targetId: 1, missing: 0, extra: 1}))).toEqual({kind: "extra", word: "1 extra"})
    expect(driftOf(list({targetId: 1, missing: 1, extra: 3}))).toEqual({kind: "missing", word: "1 missing, 3 extra"})
  })

  it("says what fills a list, or nothing for one made by hand", () => {
    expect(followsOf({missing: missing()})).toBe("Contribution paid · Paid 2026-2027")
    expect(followsOf({list: list({cohortType: CohortType.COMMITTEE_MEMBERS, cohortLabel: "Sitecie"})})).toBe("Committee members · Sitecie")
    expect(followsOf({list: list({})})).toBe("Nothing")
  })

  it("groups by folder with a missing list first, then the lists following nothing, then the archive, and searches", () => {
    const overview = {
      lists: [
        list({externalId: "2", label: "Sitecie", folderLabel: "Committees", targetId: 2, cohortLabel: "Sitecie"}),
        list({externalId: "1", label: "Board", folderLabel: "Committees", targetId: 1}),
        list({externalId: "3", label: "Paid 2025-2026", folderLabel: "Contribution paid", targetId: 3}),
        list({externalId: "4", label: "Old test"}),
        list({externalId: "5", label: "Linked loose", targetId: 5}),
        list({externalId: "6", label: "LAN 2024", folderLabel: "Archive"}),
      ],
      missing: [missing(), missing({targetId: 4, cohortLabel: "Loose", folder: null})],
    }

    const groups = groupsOf(overview, "")
    expect(groups.map((group) => group.name)).toEqual(["Contribution paid", "No folder", "Committees", "Follows nothing", "Archive"])
    expect(groups[0].rows.map((row) => row.missing?.cohortLabel ?? row.list?.label)).toEqual(["Paid 2026-2027", "Paid 2025-2026"])
    expect(groups[1].rows.map((row) => row.missing?.cohortLabel ?? row.list?.label)).toEqual(["Loose", "Linked loose"])
    expect(groups[2].rows.map((row) => row.list?.label)).toEqual(["Board", "Sitecie"])
    expect(groups.map((group) => group.kind)).toEqual(["folder", "folder", "folder", "unlinked", "archive"])

    expect(groupsOf(overview, " SITE ").map((group) => group.name)).toEqual(["Committees"])
    expect(groupsOf(overview, "nothing like it")).toEqual([])
  })

  it("counts lists, folders and drift, and when the newest reconcile ran", () => {
    const facts = overviewFacts({
      lists: [
        list({folderLabel: "A", targetId: 1, missing: 2, extra: 1}),
        list({folderLabel: "A", targetId: 2, missing: 0, extra: 0}),
        list({folderLabel: "B"}),
      ],
      missing: [],
      lastReconciledAt: "2026-10-01T03:00:00Z",
    })
    expect(facts.map((fact) => [fact.value, fact.sub])).toEqual([
      ["3", "in 2 folders"],
      ["1 list", "2 people missing, 1 extra"],
      [expect.stringContaining("2026"), "Nightly, and after every change"],
    ])
    expect(overviewFacts({lists: [list({targetId: 1, missing: 1})], missing: []}).map((fact) => fact.value)).toEqual(["1", "1 list", "Never"])
  })

  it("says which expected lists are missing, or nothing when none are", () => {
    expect(missingNotice([])).toBeNull()
    expect(missingNotice([missing()])).toEqual({
      title: "1 list the site expects is missing",
      body: "Paid 2026-2027 has no list yet, so nobody in it gets mail sent to a list. Creating one puts it in its folder and fills it straight away.",
    })
    expect(missingNotice([missing(), missing({cohortLabel: "B"}), missing({cohortLabel: "C"})])?.title).toBe("3 lists the site expects are missing")
    expect(missingNotice([missing(), missing({cohortLabel: "B"}), missing({cohortLabel: "C"})])?.body).toContain("Paid 2026-2027, B and C have no list yet, so nobody in them")
  })
})
