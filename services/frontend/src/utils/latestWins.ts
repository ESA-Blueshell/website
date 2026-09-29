/**
 * Turns for requests that answer out of order, so an answer a newer request overtook is dropped.
 *
 * A search as the reader types, or a filter changed twice, can hear back from the older request
 * last. `begin` hands a request a check that stays true only while nothing has started since, and
 * `drop` retires every request in flight, for a field emptied by the reader.
 */
export function latestWins(): {begin: () => () => boolean; drop: () => void} {
  let current = 0
  return {
    begin: () => {
      const mine = ++current
      return () => mine === current
    },
    drop: () => {
      current++
    },
  }
}
