import {computed, ref, type Ref} from "vue"
import {type ListedTarget, type TargetSystem, fetchCohort, pushDriftPeople, removeDriftPeople, triggerReconcile} from "../adapters/cohorts"
import {driftRowsOf} from "../listPage"
import {systemLabel} from "../reading"

/** What is done to several lists or roles at once: compared, filled with who is missing, or cleared of who should not be on them. */
export type SyncTask = "compare" | "push" | "remove"

interface SyncItem {
  key: string
  name: string
  note: string
  target: ListedTarget
}

/**
 * Comparing, filling or clearing the ticked lists or roles of a system together. Only one that
 * follows something on the site can be compared, only one somebody is missing from filled, and only
 * one with somebody on it who should not be cleared; the rest are left out and said so first.
 */
export function useTargetSync(system: TargetSystem, ticked: Ref<ListedTarget[]>, noun: [string, string]) {
  const task = ref<SyncTask | null>(null)
  const on = systemLabel(system)

  const followed = (one: ListedTarget) => one.cohortId != null && one.targetId != null
  const fits = (one: ListedTarget) => {
    if (!followed(one)) return false
    if (task.value === "push") return (one.missing ?? 0) > 0
    if (task.value === "remove") return (one.extra ?? 0) > 0
    return true
  }
  const noteOf = (one: ListedTarget) => {
    if (task.value === "push") return `${one.missing} missing`
    if (task.value === "remove") return `${one.extra} ${one.extra === 1 ? "leaves" : "leave"}`
    return one.cohortLabel ?? ""
  }

  const items = computed<SyncItem[]>(() => ticked.value.filter(fits).map((one) => ({
    key: one.externalId,
    name: one.label,
    note: noteOf(one),
    target: one,
  })))
  const skipped = computed(() => ticked.value.filter((one) => !fits(one)).map((one) => ({
    name: one.label,
    why: !followed(one) ? "Follows nothing on the site" : task.value === "remove" ? "Nobody is on it who should not be" : "Nobody is missing",
  })))

  const run = async ({target}: SyncItem): Promise<{ok: true} | {ok: false; reason: string}> => {
    const [cohortId, targetId] = [target.cohortId!, target.targetId!]
    if (task.value === "compare") return triggerReconcile(cohortId, targetId)
    const cohort = await fetchCohort(cohortId)
    if (!cohort) return {ok: false, reason: "What it follows could not be read."}
    if (task.value === "remove") {
      const additional = driftRowsOf(cohort.members, system)
        .filter((one) => one.sync === "ONLY_EXTERNAL" && one.externalUserId != null)
        .map((one) => one.externalUserId!)
      if (additional.length === 0) return {ok: true}
      const answered = await removeDriftPeople(cohortId, targetId, additional)
      return answered.ok ? {ok: true} : answered
    }
    // Somebody with no account on the system cannot be added to it; they stay missing.
    const missing = driftRowsOf(cohort.members, system)
      .filter((one) => one.sync === "ONLY_HERE" && one.userId != null && !one.unreachable)
      .map((one) => one.userId!)
    if (missing.length === 0) return {ok: true}
    const answered = await pushDriftPeople(cohortId, targetId, missing)
    return answered.ok ? {ok: true} : answered
  }

  const title = computed(() => {
    if (task.value === "push") return "Add missing people"
    if (task.value === "remove") return "Remove additional people"
    return `Compare with ${on}`
  })
  const words = computed(() => {
    const count = `${items.value.length} ${items.value.length === 1 ? noun[0] : noun[1]}`
    if (task.value === "remove") {
      return {
        plan: `have people on them who are not in what they follow. Tick the ones to remove them from on ${on}. Nothing is changed yet.`,
        ask: `Remove the additional people from the ticked ${noun[1]} on ${on} now? This is not undone from here.`,
        go: "Remove them",
        doing: "Removing",
        done: "cleared",
      }
    }
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
