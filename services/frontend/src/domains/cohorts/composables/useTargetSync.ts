import {computed, ref, type Ref} from "vue"
import {type ListedTarget, type TargetSystem, fetchCohort, pushDriftPeople, triggerReconcile} from "../adapters/cohorts"
import {driftRowsOf} from "../listPage"
import {systemLabel} from "../reading"

/** What is done to several lists or roles at once: compared with their system, or filled with who is missing. */
export type SyncTask = "compare" | "push"

interface SyncItem {
  key: string
  name: string
  note: string
  target: ListedTarget
}

/**
 * Comparing or filling the ticked lists or roles of a system together. Only one that follows
 * something on the site can be compared, and only one somebody is missing from can be filled; the
 * rest are left out and said so before anything is done.
 */
export function useTargetSync(system: TargetSystem, ticked: Ref<ListedTarget[]>, noun: [string, string]) {
  const task = ref<SyncTask | null>(null)
  const on = systemLabel(system)

  const followed = (one: ListedTarget) => one.cohortId != null && one.targetId != null
  const fits = (one: ListedTarget) => followed(one) && (task.value !== "push" || (one.missing ?? 0) > 0)

  const items = computed<SyncItem[]>(() => ticked.value.filter(fits).map((one) => ({
    key: one.externalId,
    name: one.label,
    note: task.value === "push" ? `${one.missing} missing` : one.cohortLabel ?? "",
    target: one,
  })))
  const skipped = computed(() => ticked.value.filter((one) => !fits(one)).map((one) => ({
    name: one.label,
    why: followed(one) ? "Nobody is missing" : "Follows nothing on the site",
  })))

  const run = async ({target}: SyncItem): Promise<{ok: true} | {ok: false; reason: string}> => {
    const [cohortId, targetId] = [target.cohortId!, target.targetId!]
    if (task.value === "compare") return triggerReconcile(cohortId, targetId)
    const cohort = await fetchCohort(cohortId)
    if (!cohort) return {ok: false, reason: "What it follows could not be read."}
    // Somebody with no account on the system cannot be added to it; they stay missing.
    const missing = driftRowsOf(cohort.members, system)
      .filter((one) => one.sync === "ONLY_HERE" && one.userId != null && !one.unreachable)
      .map((one) => one.userId!)
    if (missing.length === 0) return {ok: true}
    const answered = await pushDriftPeople(cohortId, targetId, missing)
    return answered.ok ? {ok: true} : answered
  }

  const title = computed(() => (task.value === "push" ? "Add missing people" : `Compare with ${on}`))
  const words = computed(() => {
    const count = `${items.value.length} ${items.value.length === 1 ? noun[0] : noun[1]}`
    return task.value === "push"
      ? {
          plan: `will have the people missing from them added on ${on}. Nothing is changed yet.`,
          ask: `Add the missing people to ${count} on ${on} now?`,
          go: `Add to ${count}`,
          doing: "Adding",
          done: "filled",
        }
      : {
          plan: `will be compared with ${on}, each in a job of its own. Nothing on ${on} is changed.`,
          ask: `Compare ${count} with ${on} now?`,
          go: `Compare ${count}`,
          doing: "Queueing",
          done: "queued",
        }
  })

  return {task, items, skipped, run, title, words}
}
