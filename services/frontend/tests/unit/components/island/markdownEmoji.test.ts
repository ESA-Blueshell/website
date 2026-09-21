import {describe, expect, it} from "vitest"
import {EditorState} from "@codemirror/state"
import {CompletionContext} from "@codemirror/autocomplete"
import {emojiCompletion} from "@/components/island/markdownEmoji"

const asking = (doc: string) => {
  const state = EditorState.create({doc})
  return emojiCompletion(new CompletionContext(state, doc.length, false))
}

describe("the emoji completion", () => {
  it("offers what a half-typed name could be", () => {
    const found = asking("we are :spark")

    expect(found?.options.map(one => one.label)).toContain(":sparkles:")
  })

  it("keeps the shortcode rather than the character, which is what markdown holds", () => {
    const found = asking("we are :fire")
    const first = found?.options[0]

    expect(first?.apply).toBe(":fire:")
    expect(first?.detail).toBe("🔥")
  })

  it("lifts the shortest name, since that is the one being asked for", () => {
    const found = asking("we are :fire")

    expect(found?.options[0]?.label).toBe(":fire:")
  })

  it("says nothing about a colon that starts no name", () => {
    expect(asking("the time is 10:")).toBeNull()
    expect(asking("nothing here")).toBeNull()
  })

  it("says nothing where no emoji answers to it", () => {
    expect(asking(":zzzzqqq")).toBeNull()
  })
})
