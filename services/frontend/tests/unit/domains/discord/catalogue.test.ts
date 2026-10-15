import {describe, expect, it} from "vitest"
import {accessOf, belongsTo, type CataloguedChannel, channelGroups, type NamedRole, opensOf} from "@/domains/discord"

const channel = (fields: Partial<CataloguedChannel> & {id: string}): CataloguedChannel =>
  ({name: fields.id, kind: "TEXT", category: null, private: false, roleIds: [], game: null, ...fields}) as CataloguedChannel

const roles = new Map<string, NamedRole>([
  ["900", {id: "900", name: "Sitecie", follows: "Committee members · Sitecie"}],
  ["901", {id: "901", name: "Valorant", follows: null}],
])

describe("the Discord pages' words for channels", () => {
  it("groups channels by category, the uncategorised first and the archive last, leaving categories out", () => {
    const groups = channelGroups([
      channel({id: "1", category: "Archive"}),
      channel({id: "2", category: "Committees"}),
      channel({id: "3"}),
      channel({id: "4", kind: "CATEGORY", name: "Committees"}),
    ])

    expect(groups.map((one) => [one.name, one.channels.map((c) => c.id)])).toEqual([["No category", ["3"]], ["Committees", ["2"]], ["Archive", ["1"]]])
  })

  it("says what a channel belongs to and who gets in", () => {
    const game = {code: "CHESS", name: "Chess", kind: "CASUAL"} as CataloguedChannel["game"]
    const team = {code: "CHESS", name: "Chess", kind: "COMPETITION"} as CataloguedChannel["game"]

    expect(belongsTo(channel({id: "1", game}), roles)).toBe("Chess, casual channel")
    expect(belongsTo(channel({id: "2", game: team}), roles)).toBe("Chess, esports channel")
    expect(belongsTo(channel({id: "3", roleIds: ["900", "901"]}), roles)).toBe("Committee members · Sitecie")
    expect(belongsTo(channel({id: "4"}), roles)).toBe("Nothing")
    expect(accessOf(channel({id: "5"}), roles)).toBe("Everyone")
    expect(accessOf(channel({id: "6", private: true}), roles)).toBe("Nobody")
    expect(accessOf(channel({id: "7", private: true, roleIds: ["900", "999"]}), roles)).toBe("Only @Sitecie, @999")
  })

  it("says what a role opens: nothing, its one channel or category, or how many", () => {
    const listed = [
      channel({id: "1", name: "sitecie", roleIds: ["900"]}),
      channel({id: "2", name: "Committees", kind: "CATEGORY", roleIds: ["901", "902"]}),
      channel({id: "3", name: "lancie", roleIds: ["901", "902"]}),
      channel({id: "4", name: "lan-voice", roleIds: ["902"]}),
      channel({id: "5", name: "Esports", kind: "CATEGORY", roleIds: ["903"]}),
    ]

    expect(opensOf("999", listed)).toBe("Nothing")
    expect(opensOf("900", listed)).toBe("#sitecie")
    expect(opensOf("903", listed)).toBe("Esports")
    expect(opensOf("901", listed)).toBe("1 category, 1 channel")
    expect(opensOf("902", listed)).toBe("1 category, 2 channels")
  })
})
