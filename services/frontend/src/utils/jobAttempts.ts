/**
 * Human label for a job's attempt counter.
 *
 * The server-side `attempts` field is the 1-indexed counter of the run that
 * is currently happening (or about to happen): a freshly enqueued job shows
 * "1 attempt", a job that has run three times shows "3 attempts", and the
 * counter ticks up the moment the admin presses retry. The frontend reflects
 * that value directly.
 */
export const attemptsLabel = (attempts?: number): string => {
  const count = attempts ?? 0
  return `${count} ${count === 1 ? "attempt" : "attempts"}`
}
