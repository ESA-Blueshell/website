import {shallowRef} from "vue"

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
 *
 * What is held is held reactively, because a panel drawn for a key nobody has navigated to has
 * to redraw itself the moment that key's answer lands. Both pages used to keep a mirror of this
 * map of their own for a template to watch, word for word the same in both, and every write here
 * needed a write to the mirror beside it — a rule nothing enforced, which a read this module had
 * thrown away could quietly break. Vue's reactivity is not a browser and the tests below still
 * run without one, so a holder that said nothing when it was written to was the missing half
 * rather than a boundary worth keeping.
 *
 * Shallow, and the map replaced rather than written into: the answers are handed out by identity
 * — a band watches the array it was given and takes a new one for a new key — so making what is
 * inside them reactive would buy nothing and cost a proxy per player.
 */
export interface HeldAnswers<K, V> {
  /**
   * The answer for a key, asking the adapter only where nothing is held that is still current
   * and nothing is in flight. Two callers asking about the same key at once share one read.
   */
  ask: (key: K) => Promise<V>
  /** What is held for a key, without asking. Nothing where it has not been read. */
  held: (key: K) => V | undefined
  /**
   * Writes down an answer that arrived some other way, under the key it turned out to be about.
   *
   * For the one read that cannot name its key in advance: a game's page read without naming a
   * season is answered about whichever season the api chose, and the panel standing on that
   * season would otherwise never find it.
   */
  keep: (key: K, answer: V) => void
  /**
   * Says that what the api answered about a key — or about everything, where none is named — is
   * out of date, so the next ask reads it again.
   *
   * What is held stays readable until that new answer lands, which is the whole difference
   * between this and dropping it. A caller says this because it has just written something and
   * the api's account of the association has moved on; the band, meanwhile, is looking at the
   * answer it already has, and emptying the holder under it would swap the band for a pulsing
   * block and back again over a correction the visitor has just made.
   *
   * A read still in flight is disowned rather than awaited: it was asked on the strength of what
   * the api said before, and a caller saying this is a caller saying that is no longer true.
   */
  outdate: (key?: K) => void
  /**
   * Drops what is held for a key, or everything where none is named: not out of date, but never
   * an answer about that key in the first place.
   *
   * Which a caller can know and this cannot: an api asked about one season and answering about
   * another has answered, so the read succeeded and there is nothing here to tell it apart from
   * an answer that was wanted. Held, it would be drawn for a season it says nothing about, and
   * answered with for ever after.
   */
  drop: (key?: K) => void
}

export function heldAnswers<K, V>(read: (key: K) => Promise<V>): HeldAnswers<K, V> {
  const answers = shallowRef(new Map<K, V>())
  /** The keys whose held answer may still be drawn but may no longer be answered with. */
  const outdated = new Set<K>()
  const flights = new Map<K, {answer: Promise<V>; token: object}>()

  const keep = (key: K, answer: V) => {
    outdated.delete(key)
    answers.value = new Map(answers.value).set(key, answer)
  }

  const start = (key: K): Promise<V> => {
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
