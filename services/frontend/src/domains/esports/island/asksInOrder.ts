/**
 * Which read a page is waiting on, so a slow answer cannot land on top of a newer one.
 *
 * Seasons can be chosen faster than they can be answered, and nothing made the answers come
 * back in the order they were asked for — so a slow read of one season could land on top of a
 * fast read of another and leave the page showing a season nobody chose. Every read starts by
 * saying it is the newest, and asks again on the way back whether it still is.
 *
 * This is deliberately not the same thing as holding answers by key. `heldAnswers` decides
 * which answers are worth keeping and are shared; this decides which one a particular page is
 * still waiting on, including the seasons it is no longer showing a spinner for. A holder is
 * shared between pages and outlives them; this belongs to one page's reading of one season.
 */
/**
 * Stamps a read as the newest one. What it answers with reports, whenever it is asked,
 * whether that read is still the newest — so a read checks it both before it writes and
 * before it puts the spinner away.
 */
export type BeginsARead = () => () => boolean

export function asksInOrder(): BeginsARead {
  let asking = 0
  return () => {
    const mine = (asking += 1)
    return () => mine === asking
  }
}
