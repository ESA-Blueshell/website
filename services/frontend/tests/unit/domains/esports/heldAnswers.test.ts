import {describe, expect, it, vi} from "vitest"
import {watchEffect} from "vue"
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

  it("lets a read disowned mid-flight land without writing what it brought", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const stale = held.ask(7)
    held.outdate()
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
    held.outdate()
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

  it("drops one key without dropping the others", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const autumn = held.ask(7)
    const spring = held.ask(8)
    source.answer(7, "autumn")
    source.answer(8, "spring")
    await Promise.all([autumn, spring])
    // Never an answer about 7 in the first place, which only the caller can know.
    held.drop(7)

    expect(held.held(7)).toBeUndefined()
    expect(held.held(8)).toBe("spring")
    void held.ask(7)
    expect(source.read).toHaveBeenCalledTimes(3)
  })

  it("asks again about a key it has been told is out of date", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const first = held.ask(7)
    source.answer(7, "autumn")
    await first
    held.outdate()
    void held.ask(7)

    expect(source.read).toHaveBeenCalledTimes(2)
  })

  it("goes on answering with what it holds while a key called out of date is read again", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    const first = held.ask(7)
    source.answer(7, "autumn")
    await first
    held.outdate()
    void held.ask(7)

    // What a band is looking at is left where it can read it: a correction the visitor has just
    // made must not swap the band for a pulsing block and back again.
    expect(held.held(7)).toBe("autumn")
    source.answer(7, "autumn, corrected", 1)
    await settle()
    expect(held.held(7)).toBe("autumn, corrected")
  })

  it("keeps an answer that arrived under a key nobody asked about", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    // The read that cannot name its key: whoever made it learns the key from the answer.
    held.keep(7, "autumn")

    expect(held.held(7)).toBe("autumn")
    await expect(held.ask(7)).resolves.toBe("autumn")
    expect(source.read).not.toHaveBeenCalled()
  })

  it("says so when an answer lands, so a panel drawn for that key redraws", async () => {
    const source = deferred()
    const held = heldAnswers(source.read)

    // What the mirrors each page used to keep were for, and the whole of why this is reactive:
    // a panel is drawn for a key before that key has been answered about.
    const seen: (string | undefined)[] = []
    watchEffect(() => seen.push(held.held(7)))
    void held.ask(7)
    source.answer(7, "autumn")
    await settle()

    expect(seen).toEqual([undefined, "autumn"])
  })
})
