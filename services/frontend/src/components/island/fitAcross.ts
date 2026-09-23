/**
 * How many items fit across [width] when none may be narrower than [narrowest], never fewer than
 * [fewest].
 *
 * A row sized by this shows a whole number of items, each widened to fill the row exactly, and
 * steps to one fewer only once they would drop below [narrowest]. Counting by width rather than by
 * breakpoint keeps an item's shape at every window size and zoom level: a fixed count stretches
 * each item as the window grows. [fewest] outranks [narrowest], for a row that reads better as
 * two narrow items than one wide one on a phone.
 */
export function fitAcross(width: number, narrowest: number, fewest = 1): number {
  return Math.max(fewest, Math.floor(width / narrowest))
}
