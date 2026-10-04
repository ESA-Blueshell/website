import {describe, expect, it} from "vitest"
import {
  accessOf,
  belongsTo,
  catalogueFacts,
  channelGroups,
  differsOf,
  openingDiffers,
  openingKind,
  opensOf,
  policyWords,
  roleAccessWord,
  type CataloguedChannel,
  type NamedRole,
} from "@/domains/discord"
import {ChannelAccess, RoleAccess} from "@/services/api"

const channel = (over: Partial<CataloguedChannel>): CataloguedChannel => ({id: "1", name: "x", kind: "TEXT", private: false, roleIds: [], ...over})
const {HIDDEN, READ, WRITE} = ChannelAccess
const roles = new Map<string, NamedRole>([
  ["900", {id: "900", name: "Sitecie", follows: "Sitecie"}],
  ["901", {id: "901", name: "Activist", follows: null}],
])

describe("the Discord catalogue words", () => {
  it("groups channels by category, the uncategorised first and the archive last, leaving categories out", () => {
    const groups = channelGroups([
      channel({id: "a", category: "Archive"}),
      channel({id: "c", kind: "CATEGORY", name: "Games"}),
      channel({id: "g", category: "Games"}),
      channel({id: "n"}),
      channel({id: "g2", category: "Games"}),
    ])

    expect(groups.map((one) => [one.name, one.channels.map((it) => it.id)])).toEqual([
      ["No category", ["n"]],
      ["Games", ["g", "g2"]],
      ["Archive", ["a"]],
    ])
  })

  it("says what a channel belongs to: its game, what fills its roles, or nothing", () => {
    expect(belongsTo(channel({game: {code: "VALO", name: "Valorant", kind: "CASUAL"}}), roles)).toBe("Game Valorant")
    expect(belongsTo(channel({game: {code: "VALO", name: "Valorant", kind: "COMPETITION"}}), roles)).toBe("Esports Valorant")
    expect(belongsTo(channel({roleIds: ["900", "901", "902"]}), roles)).toBe("Sitecie")
    expect(belongsTo(channel({roleIds: ["901"]}), roles)).toBe("Nothing")
  })

  it("words an access policy, a private channel's roles and where Discord differs", () => {
    expect(policyWords({everyone: READ, members: WRITE})).toBe("Everyone reads, @Member writes")
    expect(policyWords({everyone: WRITE, members: WRITE})).toBe("Everyone writes")
    expect(policyWords({everyone: HIDDEN, members: HIDDEN})).toBe("Hidden")
    expect(policyWords({everyone: HIDDEN, members: READ})).toBe("@Member reads")

    const kept = {kept: {everyone: READ, members: WRITE}, actual: {everyone: WRITE, members: WRITE}, differs: true}
    expect(accessOf(channel({access: kept}), roles)).toBe("Everyone reads, @Member writes")
    expect(differsOf(channel({access: kept}))).toBe("Everyone writes")
    expect(differsOf(channel({}))).toBe("No")
    expect(accessOf(channel({}), roles)).toBe("Everyone")
    expect(accessOf(channel({private: true}), roles)).toBe("Nobody")
    expect(accessOf(channel({private: true, roleIds: ["900", "77"]}), roles)).toBe("Only @Sitecie, @77")
  })

  it("says what a role opens", () => {
    const opened = [
      channel({id: "1", kind: "CATEGORY", name: "Games", roleIds: ["900"]}),
      channel({id: "2", kind: "CATEGORY", name: "Voice", roleIds: ["900"]}),
      channel({id: "3", name: "sitecie", roleIds: ["900", "901"]}),
      channel({id: "4", name: "board", roleIds: ["902"]}),
      channel({id: "5", kind: "CATEGORY", name: "Esports", roleIds: ["903"]}),
    ]
    expect(opensOf("900", opened)).toBe("2 categories, 1 channel")
    expect(opensOf("901", opened)).toBe("#sitecie")
    expect(opensOf("903", opened)).toBe("Esports")
    expect(opensOf("999", opened)).toBe("Nothing")
    expect(opensOf("900", opened.filter((one) => one.kind === "CATEGORY"))).toBe("2 categories")
    expect(opensOf("900", opened.filter((one) => one.id !== "1"))).toBe("1 category, 1 channel")
  })

  it("counts the kept roles, the drift, the people with no Discord and the differing channels", () => {
    const facts = catalogueFacts(
      [{targetId: 1, missing: 1, extra: 2, unreachable: 3}, {targetId: 2, missing: 0, extra: 0}, {targetId: null}],
      1,
      [channel({access: {actual: {everyone: READ, members: READ}, differs: true}}), channel({kind: "CATEGORY"}), channel({})],
    )

    expect(facts.map((one) => [one.value, one.sub])).toEqual([
      ["2", "1 still to create"],
      ["1 role", "3 people with no Discord linked"],
      ["2", "1 differs from its access policy"],
    ])
    expect(catalogueFacts([{targetId: 1, unreachable: 1}], 0, [])[1]!.sub).toBe("1 person with no Discord linked")
    expect(catalogueFacts([], 0, [])[2]!.sub).toBe("0 differ from its access policy")
  })

  it("words a role's access, where Discord differs and what an opening is", () => {
    expect([RoleAccess.WRITE, RoleAccess.READ, RoleAccess.SPEAK].map(roleAccessWord)).toEqual(["Read and write", "Read only", "Join and speak"])
    const lounge = {id: "1", name: "lounge", kind: "TEXT" as const}
    expect(openingDiffers({channel: lounge, kept: RoleAccess.WRITE, actual: RoleAccess.WRITE, differs: false})).toBeNull()
    expect(openingDiffers({channel: lounge, actual: RoleAccess.READ, differs: true})).toBe("On Discord only, at read only")
    expect(openingDiffers({channel: lounge, kept: RoleAccess.READ, differs: true})).toBe("Not open on Discord, on the site read only")
    expect(openingDiffers({channel: lounge, kept: RoleAccess.WRITE, actual: RoleAccess.SPEAK, differs: true}))
      .toBe("On Discord join and speak, on the site read and write")

    const listed = [channel({id: "9", kind: "CATEGORY", name: "Games"}), channel({category: "Games"}), channel({id: "2", category: "Games"}), channel({id: "3", category: "Voice"})]
    expect(openingKind({channel: {id: "9", name: "Games", kind: "CATEGORY"}, differs: false}, listed)).toBe("Category · 2 channels")
    expect(openingKind({channel: {id: "8", name: "Voice", kind: "CATEGORY"}, differs: false}, listed)).toBe("Category · 1 channel")
    expect(openingKind({channel: {id: "3", name: "Lounge", kind: "VOICE"}, differs: false}, listed)).toBe("Voice channel")
    expect(openingKind({channel: lounge, differs: false}, listed)).toBe("Channel")
  })
})
