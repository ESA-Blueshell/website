/**
 * Which read a page is waiting on, so a slow answer cannot land on top of a newer one.
 *
 * Seasons are chosen faster than they are answered and nothing makes the answers come back in
 * order, so a slow read of one could land on top of a fast read of another. Every read starts by
 * saying it is the newest and asks again on the way back whether it still is. Deliberately not the
 * same thing as holding answers by key: `heldAnswers` decides which answers are worth keeping and
 * is shared between pages, while this belongs to one page's reading of one season.
 */

/**
 * Whether the read that asked for this is still the newest one, asked whenever it is wanted — so
 * a read checks it both before it writes and before it puts the spinner away.
 */
export type StillWanted = () => boolean

/** Stamps a read as the newest one, and answers with the way to ask whether it still is. */
export type BeginsARead = () => StillWanted

export function asksInOrder(): BeginsARead {
  let asking = 0
  return () => {
    const mine = (asking += 1)
    return () => mine === asking
  }
}
