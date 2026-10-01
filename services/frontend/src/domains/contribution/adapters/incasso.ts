/**
 * Incasso runs: who can be collected from in a period, starting a run and reading one back.
 * Every answer masks an account to its last four.
 */
import {
  downloadIncassoFile,
  findIncassoRun,
  type IncassoCandidate,
  type IncassoCollection,
  IncassoLeftOut,
  type IncassoRunSummary,
  markIncassoRunSubmitted,
  type IncassoRunView,
  planIncasso,
  startIncassoRun,
  type StartIncassoRunRequest,
} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {refusable} from "../refusals"

export type {IncassoCandidate, IncassoCollection, IncassoRunSummary, IncassoRunView, StartIncassoRunRequest}
export {IncassoLeftOut}

/** Everybody on incasso in the period, or nobody where they could not be read. */
export const readIncassoPlan = (periodId: number): Promise<IncassoCandidate[]> => readOr(planIncasso({path: {periodId}}), [])

/** Records the run and emails each member their incasso notification. */
export const startIncasso = (periodId: number, body: StartIncassoRunRequest): Promise<Saved<IncassoRunView> | Refused> =>
  refusable(startIncassoRun({path: {periodId}, body}), "The incasso could not be started.")

/** One run as its members were told it, or nothing where it could not be read. */
export const readIncassoRun = (runId: number): Promise<IncassoRunView | null> => readOr(findIncassoRun({path: {runId}}), null)

/** One of the run's files for ING, filled in by the api as it answers; nothing keeps a copy. */
export async function fetchIncassoFile(runId: number, part: number): Promise<{ok: true; file: Blob} | Refused> {
  const answered = await refusable(downloadIncassoFile({path: {runId}, query: {part}}), "The file for ING could not be made.")
  return answered.ok ? {ok: true, file: answered.saved as Blob} : answered
}

/** The board put the run in ING and confirmed it there. */
export const saveSubmitted = (runId: number): Promise<Saved<IncassoRunView> | Refused> =>
  refusable(markIncassoRunSubmitted({path: {runId}}), "The incasso could not be marked as submitted.")
