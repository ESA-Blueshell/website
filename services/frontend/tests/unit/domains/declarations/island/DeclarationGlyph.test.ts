import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import DeclarationGlyph from "@/domains/declarations/island/DeclarationGlyph.vue"

describe("the declaration glyph", () => {
  it("draws each mark it is named for", () => {
    const paths = (name: string) => mount(DeclarationGlyph, {props: {name}}).findAll("path").length

    expect([paths("plus"), paths("receipt"), paths("bank"), paths("paid")]).toEqual([1, 2, 2, 2])
  })
})
