import {describe, expect, it, vi} from "vitest"
import {heldAnswers} from "@/domains/esports/island/heldAnswers"

/**
 * A read whose answers arrive when the test says so, which is the only way to prove that a
 * second ask lands while the first is still in flight rather than after it. Every read of a
 * key is kept, so a test can answer them out of the order they were asked in.
 */
const deferred = () => {
  const flights = new Map<number, {resolve: (value: string) => void; reject: (reason: Error) => void}[]>()
  const read = vi.fn((key: number) => new Promise<string>((resolve, reject) => {
    flights.set(key, [...(flights.get(key) ?? []), {resolve, reject}])
  }))
  return {
    read,
    /** Answers the nth read of a key, counting from the first one asked for. */
    answer: (key: number, value: string, nth = 0) => flights.get(key)![nth]!.resolve(value),
    refuse: (key: number, reason: string, nth = 0) => flights.get(key)![nth]!.reject(new Error(reason)),
  }
}

/** Lets every microtask that is ready run, so a settled read has written what it brought. */
const settle = () => new Promise(resolve => setTimeout(resolve, 0))

describe("heldAnswers", () => {
  it("asks about a key it has not been asked about before", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const asked = held.ask(7)
    source.answer(7, "autumn")

    await expect(asked).resolves.toBe("autumn")
    expect(source.read).toHaveBeenCalledWith(7)
  })

  it("does not ask a second time about a key whose read is still in flight", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const first = held.ask(7)
    const second = held.ask(7)
    source.answer(7, "autumn")

    expect(source.read).toHaveBeenCalledTimes(1)
    await expect(first).resolves.toBe("autumn")
    await expect(second).resolves.toBe("autumn")
  })

  it("does not ask a second time about a key it already holds an answer for", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const first = held.ask(7)
    source.answer(7, "autumn")
    await first

    await expect(held.ask(7)).resolves.toBe("autumn")
    expect(source.read).toHaveBeenCalledTimes(1)
  })

  it("asks separately about each key, and keeps both answers", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const spring = held.ask(8)
    const autumn = held.ask(7)
    source.answer(7, "autumn")
    source.answer(8, "spring")
    await Promise.all([spring, autumn])

    expect(held.held(7)).toBe("autumn")
    expect(held.held(8)).toBe("spring")
  })

  it("holds nothing for a key nobody has asked about", () => {
    const held = heldAnswers(deferred().read)

    expect(held.held(7)).toBeUndefined()
  })

  it("can be asked about a key ahead of anybody arriving there", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    // Nobody awaits this; the answer is wanted in hand by the time somebody asks for it.
    void held.ask(8)
    source.answer(8, "spring")
    await settle()

    expect(held.held(8)).toBe("spring")
    await expect(held.ask(8)).resolves.toBe("spring")
    expect(source.read).toHaveBeenCalledTimes(1)
  })

  it("lets a read that was forgotten mid-flight land without writing what it brought", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const stale = held.ask(7)
    held.forget()
    source.answer(7, "stale")
    await stale
    await settle()

    expect(held.held(7)).toBeUndefined()
  })

  it("does not let a slow answer land on top of the one asked for after it", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    void held.ask(7)
    // A write happened in between, so the first read is disowned and the key asked about again.
    held.forget()
    void held.ask(7)
    source.answer(7, "newer", 1)
    await settle()
    // The first flight lands last, and finds it is no longer the flight this key waits on.
    source.answer(7, "stale", 0)
    await settle()

    expect(held.held(7)).toBe("newer")
    expect(source.read).toHaveBeenCalledTimes(2)
  })

  it("holds nothing for a read that was refused, so the next ask tries again", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const refused = held.ask(7)
    source.refuse(7, "no")
    await expect(refused).rejects.toThrow("no")

    void held.ask(7)
    expect(source.read).toHaveBeenCalledTimes(2)
  })

  it("forgets one key without forgetting the others", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const autumn = held.ask(7)
    const spring = held.ask(8)
    source.answer(7, "autumn")
    source.answer(8, "spring")
    await Promise.all([autumn, spring])
    held.forget(7)

    expect(held.held(7)).toBeUndefined()
    expect(held.held(8)).toBe("spring")
  })

  it("asks again about a key it has been told to forget", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const first = held.ask(7)
    source.answer(7, "autumn")
    await first
    held.forget()
    void held.ask(7)

    expect(source.read).toHaveBeenCalledTimes(2)
  })
})
