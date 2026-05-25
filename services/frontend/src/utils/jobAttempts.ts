/**
 * Human label for a job's attempt counter.
 *
 * The server-side `attempts` field is the number of attempts that have already
 * been made (incremented by `JobExecutionService.requeue` and `markRetryScheduled`).
 * The label reflects that number directly — a job that has failed four times shows
 * "4 attempts", a job that has never run shows "0 attempts". The previous
 * implementation added `+ 1`, which made server `attempts=0` show as "1 attempt"
 * and made successful retries look like the counter regressed.
 */
export const attemptsLabel = (attempts?: number): string => {
  const count = attempts ?? 0
  return `${count} ${count === 1 ? "attempt" : "attempts"}`
}
