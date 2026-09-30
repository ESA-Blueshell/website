import {computed, ref, type Ref} from "vue"
import {
  linkDriftPeople,
  proposeDriftLinks,
  pushDriftPeople,
  removeDriftPeople,
  type CohortMember,
  type LinkProposal,
  type TargetMapping,
} from "@/domains/cohorts/adapters/cohorts"

/** What the board can do about a person on one side only. Adopting is the inbound reconcile. */
export type DriftAction = "push" | "remove" | "link"

/** The people one action concerns, grouped by the target each sits on. */
export type DriftGroup = {targetId: number; people: CohortMember[]}

/** What a confirmed action will do: its people, and for a link the account each would get. */
export type DriftPlan = {action: DriftAction; groups: DriftGroup[]; proposals: LinkProposal[]}

const fits: Record<DriftAction, (row: CohortMember) => boolean> = {
  push: (row) => row.sync === "ONLY_HERE" && row.userId != null,
  remove: (row) => row.sync === "ONLY_EXTERNAL" && row.externalUserId != null,
  link: (row) => row.sync === "ONLY_EXTERNAL" && row.userId == null && row.externalUserId != null,
}

const DONE: Record<DriftAction, (count: number) => string> = {
  push: (count) => `${count} pushed.`,
  remove: (count) => `${count} removed.`,
  link: (count) => `${count} linked.`,
}

/**
 * Resolving drift on one cohort's targets, for one row or a selection: the rows an action fits,
 * a plan to confirm, and the calls it makes per target.
 */
export function useDriftResolution(
  cohortId: Ref<number | null>,
  mappings: Ref<TargetMapping[]>,
  reload: () => Promise<void>,
) {
  /** Ticked rows, by ledger row id. */
  const selection = ref<Set<number>>(new Set())
  const plan = ref<DriftPlan | null>(null)
  const working = ref(false)
  const message = ref<string | null>(null)
  const error = ref<string | null>(null)

  const toggle = (row: CohortMember) => {
    const next = new Set(selection.value)
    if (next.has(row.targetMemberId)) next.delete(row.targetMemberId)
    else next.add(row.targetMemberId)
    selection.value = next
  }

  const clear = () => {
    selection.value = new Set()
  }

  const targetIdFor = (row: CohortMember): number | null =>
    mappings.value.find((mapping) => mapping.system === row.system)?.targetId ?? null

  const canResolve = (action: DriftAction, row: CohortMember): boolean => fits[action](row) && targetIdFor(row) != null

  const grouped = (action: DriftAction, rows: CohortMember[]): DriftGroup[] => {
    const groups = new Map<number, CohortMember[]>()
    rows.filter((row) => canResolve(action, row)).forEach((row) => {
      const targetId = targetIdFor(row)!
      groups.set(targetId, [...(groups.get(targetId) ?? []), row])
    })
    return [...groups].map(([targetId, people]) => ({targetId, people}))
  }

  /** How many of the ticked rows [action] fits, which is what its button counts. */
  const selectedFor = (action: DriftAction, rows: CohortMember[]): number =>
    rows.filter((row) => selection.value.has(row.targetMemberId) && canResolve(action, row)).length

  /** Opens the plan for [action] over [rows]; a link first asks which account each contact has. */
  const prepare = async (action: DriftAction, rows: CohortMember[]) => {
    error.value = null
    message.value = null
    const groups = grouped(action, rows)
    if (groups.length === 0 || cohortId.value == null) return
    const proposals: LinkProposal[] = []
    if (action === "link") {
      for (const group of groups) {
        const answer = await proposeDriftLinks(cohortId.value, group.targetId, group.people.map((row) => row.externalUserId!))
        if (!answer.ok) {
          error.value = answer.reason
          return
        }
        proposals.push(...answer.saved)
      }
    }
    plan.value = {action, groups, proposals}
  }

  const cancel = () => {
    plan.value = null
  }

  /** Links only the contacts an account was found for; the rest stay as they are. */
  const linksFor = (group: DriftGroup, proposals: LinkProposal[]) => {
    const ids = new Set(group.people.map((row) => row.externalUserId))
    return proposals
      .filter((proposal) => ids.has(proposal.externalUserId) && proposal.userId != null)
      .map((proposal) => ({externalUserId: proposal.externalUserId, userId: proposal.userId!}))
  }

  const run = async (current: DriftPlan, group: DriftGroup, id: number): Promise<{count: number; refused: string | null}> => {
    if (current.action === "push") {
      const answer = await pushDriftPeople(id, group.targetId, group.people.map((row) => row.userId!))
      return answer.ok ? {count: answer.saved, refused: null} : {count: 0, refused: answer.reason}
    }
    if (current.action === "remove") {
      const answer = await removeDriftPeople(id, group.targetId, group.people.map((row) => row.externalUserId!))
      return answer.ok ? {count: answer.saved, refused: null} : {count: 0, refused: answer.reason}
    }
    const links = linksFor(group, current.proposals)
    if (links.length === 0) return {count: 0, refused: null}
    const answer = await linkDriftPeople(id, group.targetId, links)
    if (!answer.ok) return {count: 0, refused: answer.reason}
    const taken = answer.saved.conflicts.length
    return {count: answer.saved.linked, refused: taken > 0 ? `${taken} already belong to another account.` : null}
  }

  /** Carries out the open plan, target by target, then reloads the page's rows. */
  const confirm = async () => {
    const current = plan.value
    const id = cohortId.value
    if (current == null || id == null) return
    working.value = true
    let count = 0
    const refusals: string[] = []
    try {
      for (const group of current.groups) {
        const outcome = await run(current, group, id)
        count += outcome.count
        if (outcome.refused) refusals.push(outcome.refused)
      }
    } finally {
      working.value = false
    }
    plan.value = null
    clear()
    message.value = DONE[current.action](count)
    error.value = refusals.length > 0 ? refusals.join(" ") : null
    await reload()
  }

  /** Link one contact to an account picked by hand; answers the account already holding it, if any. */
  const linkOne = async (row: CohortMember, userId: number): Promise<number | null> => {
    const targetId = targetIdFor(row)
    if (targetId == null || cohortId.value == null || row.externalUserId == null) return null
    const answer = await linkDriftPeople(cohortId.value, targetId, [{externalUserId: row.externalUserId, userId}])
    if (!answer.ok) {
      error.value = answer.reason
      return null
    }
    const conflict = answer.saved.conflicts.at(0)
    if (conflict) return conflict.existingUserId
    message.value = DONE.link(answer.saved.linked)
    await reload()
    return null
  }

  const planCount = computed(() => plan.value?.groups.reduce((sum, group) => sum + group.people.length, 0) ?? 0)

  return {selection, plan, planCount, working, message, error, toggle, clear, canResolve, selectedFor, prepare, cancel, confirm, linkOne}
}
