/**
 * Answers kept by the key they were asked for.
 *
 * Both esports pages read one season at a time and each used to hold exactly one answer, so a
 * visitor walking back and forth along the strip re-asked the api for a season it had already
 * described a moment earlier. Worse, neither page could have two seasons in hand at once,
 * which is what a page being dragged towards its neighbour needs.
 *
 * So the holding lives here instead: an answer is kept under its key for as long as the page
 * is open, a key already in flight is not asked about twice, and a key can be asked about
 * before anybody has navigated to it. What the answer is and what a key means are the caller's
 * business — the two pages read different shapes from different adapters, and the point of
 * this module is that neither has to pretend otherwise.
 */
export interface HeldAnswers<K, V> {
  /**
   * The answer for a key, asking the adapter only where nothing is held and nothing is in
   * flight. Two callers asking about the same key at the same time share one read.
   */
  ask: (key: K) => Promise<V>
  /** What is already held for a key, without asking. Nothing where it has not been read. */
  held: (key: K) => V | undefined
  /**
   * Forgets a key, or everything where none is named, so the next ask reads again.
   *
   * A read still in flight when this is called is abandoned rather than awaited: it was asked
   * on the strength of what the api said before, and a caller forgetting is a caller saying
   * that is no longer true.
   */
  forget: (key?: K) => void
}

export function heldAnswers<K, V>(read: (key: K) => Promise<V>): HeldAnswers<K, V> {
  const answers = new Map<K, V>()
  const flights = new Map<K, {answer: Promise<V>; token: object}>()

  const start = (key: K): Promise<V> => {
    // Every flight carries a token so the one that lands can tell whether it is still the
    // flight this key is waiting on. A read that has been forgotten, or replaced by a later
    // one for the same key, may not write what it brought back: it would put the answer the
    // caller has just disowned on top of the one it went looking for.
    const token = {}
    const mine = () => flights.get(key)?.token === token
    const answer = read(key).then(
      (value) => {
        if (mine()) {
          flights.delete(key)
          answers.set(key, value)
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
      if (answers.has(key)) return Promise.resolve(answers.get(key) as V)
      return flights.get(key)?.answer ?? start(key)
    },
    held: (key) => answers.get(key),
    forget: (key) => {
      if (key === undefined) {
        answers.clear()
        flights.clear()
        return
      }
      answers.delete(key)
      flights.delete(key)
    },
  }
}
