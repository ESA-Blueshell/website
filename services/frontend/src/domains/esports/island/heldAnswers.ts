import {shallowRef} from "vue"

/**
 * Answers kept by the key they were asked for.
 *
 * An answer is kept under its key for as long as the page is open, a key already in flight is not
 * asked about twice, and a key can be asked about before anybody has navigated to it — which is
 * what a page being dragged towards its neighbour needs. What a key means and what an answer is
 * are the caller's, the two pages reading different shapes from different adapters. Held
 * reactively, since a panel drawn for a key nobody has navigated to must redraw the moment that
 * key's answer lands, but shallow and replaced rather than written into: answers are handed out
 * by identity, so making their contents reactive costs a proxy per player and buys nothing.
 */
export interface HeldAnswers<V> {
  /**
   * The answer for a key, asking the adapter only where nothing is held that is still current
   * and nothing is in flight. Two callers asking about the same key at once share one read.
   */
  ask: (key: number) => Promise<V>
  /** What is held for a key, without asking. Nothing where it has not been read. */
  held: (key: number) => V | undefined
  /**
   * Writes down an answer that arrived some other way, under the key it turned out to be about.
   *
   * For the one read that cannot name its key in advance: a game's page read without naming a
   * season is answered about whichever season the api chose, and the panel standing on that
   * season would otherwise never find it.
   */
  keep: (key: number, answer: V) => void
  /**
   * Says that what the api answered about a key — or about everything, where none is named — is
   * out of date, so the next ask reads it again.
   *
   * What is held stays readable until the new answer lands, which is the whole difference between
   * this and dropping it: a caller says this having just written something, and emptying the
   * holder would swap the band for a pulsing block and back over a correction the visitor made.
   * A read in flight is disowned rather than awaited, having been asked on the strength of what
   * the api said before.
   */
  outdate: (key?: number) => void
  /**
   * Drops what is held for a key, or everything where none is named: not out of date, but never
   * an answer about that key in the first place.
   *
   * Which a caller can know and this cannot: an api asked about one season and answering about
   * another has answered, so the read succeeded and there is nothing here to tell it apart from
   * an answer that was wanted. Held, it would be drawn for a season it says nothing about, and
   * answered with for ever after.
   */
  drop: (key?: number) => void
}

export function heldAnswers<V>(read: (key: number) => Promise<V>): HeldAnswers<V> {
  const answers = shallowRef(new Map<number, V>())
  /** The keys whose held answer may still be drawn but may no longer be answered with. */
  const outdated = new Set<number>()
  const flights = new Map<number, {answer: Promise<V>; token: object}>()

  const keep = (key: number, answer: V) => {
    outdated.delete(key)
    answers.value = new Map(answers.value).set(key, answer)
  }

  const start = (key: number): Promise<V> => {
    // Every flight carries a token so the one that lands can tell whether it is still the
    // flight this key is waiting on. A read that has been disowned, or replaced by a later
    // one for the same key, may not write what it brought back: it would put the answer the
    // caller has just called out of date on top of the one it went looking for.
    const token = {}
    const mine = () => flights.get(key)?.token === token
    const answer = read(key).then(
      (value) => {
        if (mine()) {
          flights.delete(key)
          keep(key, value)
        }
        return value
      },
      (error: unknown) => {
        // Nothing is held for a read that failed, so the next ask tries again. A page that
        // shows a refusal is showing it because the adapter answered one, not because this
        // remembered it.
        if (mine()) flights.delete(key)
        throw error
      },
    )
    flights.set(key, {answer, token})
    return answer
  }

  return {
    ask: (key) => {
      const current = answers.value.has(key) && !outdated.has(key)
      if (current) return Promise.resolve(answers.value.get(key) as V)
      return flights.get(key)?.answer ?? start(key)
    },
    held: (key) => answers.value.get(key),
    keep,
    outdate: (key) => {
      if (key === undefined) {
        answers.value.forEach((_, held) => outdated.add(held))
        flights.clear()
        return
      }
      outdated.add(key)
      flights.delete(key)
    },
    drop: (key) => {
      if (key === undefined) {
        answers.value = new Map()
        outdated.clear()
        flights.clear()
        return
      }
      const left = new Map(answers.value)
      left.delete(key)
      answers.value = left
      outdated.delete(key)
      flights.delete(key)
    },
  }
}
