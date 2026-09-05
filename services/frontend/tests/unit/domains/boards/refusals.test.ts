import {beforeEach, describe, expect, it, vi} from "vitest"
import {boardHoldsMembers, reasonFor, sentenceFor} from "@/domains/boards/refusals"
// Imported here rather than inside a test: `vi.mock` is hoisted above the imports, so the mock
// binds either way, and loading a module inside a test spends the test's own timeout on
// transforming it. That is what made this file flaky — under a loaded machine the import
// outran the five seconds and the test failed having asserted nothing.
import {dropBoard} from "@/domains/boards/adapters/boards"

// Hoisted with the `vi.mock` that reads it: the factory runs before the module body, so a
// plain `const` here is still in its temporal dead zone when the adapter is imported.
const {mockDeleteBoard} = vi.hoisted(() => ({mockDeleteBoard: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    deleteBoard: mockDeleteBoard,
  }
})

describe("boardHoldsMembers", () => {
  it("says how many members are in the way", () => {
    const said = boardHoldsMembers(9, 5)

    expect(said).toContain("Board 9 still has 5 members on it")
    expect(said).toContain("so it cannot be removed")
  })

  it("says one member singly", () => {
    expect(boardHoldsMembers(3, 1)).toContain("still has 1 member on it")
  })
})

describe("sentenceFor", () => {
  it("composes the removal refusal from the count, not from a sentence the api sent", () => {
    expect(sentenceFor({code: "BoardHoldsMembers", number: 9, members: 5}))
      .toBe(boardHoldsMembers(9, 5))
  })

  it("answers nothing for a code it has not been taught, so the caller falls back", () => {
    expect(sentenceFor({code: "SomethingAddedLater", detail: "Refused."})).toBeNull()
  })

  it("answers nothing for a body carrying no code at all", () => {
    expect(sentenceFor({detail: "Validation failed"})).toBeNull()
    expect(sentenceFor(null)).toBeNull()
  })
})

describe("reasonFor", () => {
  it("falls back to the api's own summary for a code it does not know", () => {
    expect(reasonFor({code: "Later", detail: "Refused."}, "fallback")).toBe("Refused.")
  })

  it("falls back to the given sentence when the body says nothing", () => {
    expect(reasonFor(null, "The board could not be removed.")).toBe("The board could not be removed.")
  })
})

describe("dropBoard", () => {
  beforeEach(() => {
    mockDeleteBoard.mockReset()
  })

  it("answers the refusal as a reason naming the members, since the sdk does not throw", async () => {
    mockDeleteBoard.mockResolvedValue({error: {code: "BoardHoldsMembers", number: 9, members: 5}})

    const answer = await dropBoard(4)

    expect(answer).toEqual({ok: false, reason: boardHoldsMembers(9, 5)})
  })

  it("answers ok for a board that removes cleanly", async () => {
    mockDeleteBoard.mockResolvedValue({data: undefined})

    expect(await dropBoard(10)).toEqual({ok: true})
  })
})
