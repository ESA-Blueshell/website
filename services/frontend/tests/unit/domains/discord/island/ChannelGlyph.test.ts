import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ChannelGlyph from "@/domains/discord/island/ChannelGlyph.vue"
import {type CataloguedChannel, channelGroups, isArchive} from "@/domains/discord"

describe("a channel's glyph", () => {
  it("says which kind of channel it is, for a reader who cannot see the mark", () => {
    const said = (props: {voice?: boolean; locked?: boolean}) => mount(ChannelGlyph, {props}).get("[role=img]").attributes("aria-label")

    expect(said({})).toBe("Public text channel")
    expect(said({locked: true})).toBe("Private text channel")
    expect(said({voice: true})).toBe("Public voice channel")
    expect(said({voice: true, locked: true})).toBe("Private voice channel")
  })
})

describe("the archive", () => {
  it("is every category with Archive in its name, and reads last", () => {
    expect([isArchive("Archive"), isArchive("Archive 2023"), isArchive("old archives"), isArchive("Games"), isArchive(null)]).toEqual([true, true, true, false, false])

    const channel = (name: string, category: string | null): CataloguedChannel => ({id: name, name, kind: "TEXT", category, private: false, roleIds: []})
    const groups = channelGroups([channel("old", "Archive 2023"), channel("valo", "Games"), channel("rules", null)])
    expect(groups.map((one) => one.name)).toEqual(["No category", "Games", "Archive 2023"])
  })
})
