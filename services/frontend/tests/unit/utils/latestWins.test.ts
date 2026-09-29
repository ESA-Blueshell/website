import {describe, expect, it} from "vitest"
import {latestWins} from "@/utils/latestWins"

describe("latestWins", () => {
  it("keeps a request the newest until another begins", () => {
    const turns = latestWins()
    const first = turns.begin()
    expect(first()).toBe(true)

    const second = turns.begin()

    expect(first()).toBe(false)
    expect(second()).toBe(true)
  })

  it("retires every request in flight when dropped", () => {
    const turns = latestWins()
    const asked = turns.begin()

    turns.drop()

    expect(asked()).toBe(false)
  })

  it("keeps each set of turns apart", () => {
    const one = latestWins()
    const other = latestWins()
    const asked = one.begin()

    other.begin()

    expect(asked()).toBe(true)
  })
})
