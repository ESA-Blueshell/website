import {beforeEach, describe, expect, it, vi} from "vitest"
import {boardHoldsSeats, reasonFor, sentenceFor} from "@/domains/boards/refusals"

const mockDeleteBoard = vi.fn()

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    deleteBoard: mockDeleteBoard,
  }
})

describe("boardHoldsSeats", () => {
  it("says how many seats are in the way", () => {
    const said = boardHoldsSeats(9, 5)

    expect(said).toContain("Board 9 still has 5 seats on it")
    expect(said).toContain("so it cannot be removed")
  })

  it("says one seat singly", () => {
    expect(boardHoldsSeats(3, 1)).toContain("still has 1 seat on it")
  })
})

describe("sentenceFor", () => {
  it("composes the removal refusal from the count, not from a sentence the api sent", () => {
    expect(sentenceFor({code: "BoardHoldsSeats", number: 9, seats: 5}))
      .toBe(boardHoldsSeats(9, 5))
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

  it("answers the refusal as a reason naming the seats, since the sdk does not throw", async () => {
    mockDeleteBoard.mockResolvedValue({error: {code: "BoardHoldsSeats", number: 9, seats: 5}})
    const {dropBoard} = await import("@/domains/boards/adapters/boards")

    const answer = await dropBoard(4)

    expect(answer).toEqual({ok: false, reason: boardHoldsSeats(9, 5)})
  })

  it("answers ok for a board that removes cleanly", async () => {
    mockDeleteBoard.mockResolvedValue({data: undefined})
    const {dropBoard} = await import("@/domains/boards/adapters/boards")

    expect(await dropBoard(10)).toEqual({ok: true})
  })
})
