import {describe, expect, it} from "vitest"
import {refusalReader} from "@/utils/refusals"

interface Body {
  code?: string
  what?: string
}

const {sentenceFor, reasonFor} = refusalReader<Body>({
  ThingHeldBack: r => `The ${r.what} is held back.`,
})

describe("sentenceFor", () => {
  it("composes the sentence for a code the domain taught it", () => {
    expect(sentenceFor({code: "ThingHeldBack", what: "board"})).toBe("The board is held back.")
  })

  it("answers nothing for a code it has not been taught, so the caller falls back", () => {
    expect(sentenceFor({code: "SomethingAddedLater"})).toBeNull()
  })

  it("answers nothing for a body carrying no code at all", () => {
    expect(sentenceFor({detail: "Refused."})).toBeNull()
    expect(sentenceFor(null)).toBeNull()
    expect(sentenceFor(undefined)).toBeNull()
  })
})

describe("reasonFor", () => {
  it("prefers the composed sentence over everything the api sent", () => {
    const said = reasonFor(
      {code: "ThingHeldBack", what: "board", errors: [{message: "Bad."}], detail: "Refused.", title: "Conflict"},
      "fallback",
    )

    expect(said).toBe("The board is held back.")
  })

  it("reads the field violations before the api's summary", () => {
    const said = reasonFor(
      {errors: [{message: "A name is required"}, {message: "A date is required"}], detail: "Refused."},
      "fallback",
    )

    expect(said).toBe("A name is required. A date is required")
  })

  it("reads the api's summary before its title", () => {
    expect(reasonFor({detail: "Refused.", title: "Conflict"}, "fallback")).toBe("Refused.")
  })

  it("reads the title when there is no summary", () => {
    expect(reasonFor({title: "Conflict"}, "fallback")).toBe("Conflict")
  })

  it("falls back to the given sentence when the body says nothing", () => {
    expect(reasonFor(null, "The board could not be removed.")).toBe("The board could not be removed.")
    expect(reasonFor({}, "The board could not be removed.")).toBe("The board could not be removed.")
  })
})
