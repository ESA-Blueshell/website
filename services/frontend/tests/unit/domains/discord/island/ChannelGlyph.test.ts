import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ChannelGlyph from "@/domains/discord/island/ChannelGlyph.vue"

describe("a channel's glyph", () => {
  it("says which kind of channel it is, for a reader who cannot see the mark", () => {
    const said = (props: {voice?: boolean; locked?: boolean}) => mount(ChannelGlyph, {props}).get("[role=img]").attributes("aria-label")

    expect(said({})).toBe("Public text channel")
    expect(said({locked: true})).toBe("Private text channel")
    expect(said({voice: true})).toBe("Public voice channel")
    expect(said({voice: true, locked: true})).toBe("Private voice channel")
  })
})
