import {describe, expect, it} from "vitest"
import {asksInOrder} from "@/domains/esports/island/asksInOrder"

describe("asksInOrder", () => {
  it("takes the only read there is for the newest one", () => {
    const begin = asksInOrder()

    expect(begin()()).toBe(true)
  })

  it("keeps taking a read that is still the newest, however often it is asked", () => {
    const begin = asksInOrder()

    const wanting = begin()

    expect(wanting()).toBe(true)
    expect(wanting()).toBe(true)
  })

  it("disowns a read as soon as a later one has begun", () => {
    const begin = asksInOrder()

    const first = begin()
    const second = begin()

    expect(first()).toBe(false)
    expect(second()).toBe(true)
  })

  it("takes only the last of several reads begun before any of them answered", () => {
    const begin = asksInOrder()

    const reads = [begin(), begin(), begin()]

    expect(reads.map(wanting => wanting())).toEqual([false, false, true])
  })

  it("keeps each page's reads to itself", () => {
    const index = asksInOrder()
    const gamePage = asksInOrder()

    const onIndex = index()
    gamePage()

    expect(onIndex()).toBe(true)
  })
})
