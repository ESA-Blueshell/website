import type {Fact} from "@/components/island/FactList.vue"
import type {StateKind} from "@/components/island/StateMark.vue"
import {formatDateNoSeconds} from "@/utils/timestamps"
import type {LastTidy, ListedTarget, MissingTarget, TargetOverview} from "./adapters/cohorts"
import {cohortTypeLabel} from "./cohortTypeLabels"

/** The folder Brevo's archived lists sit in; it reads last and folded. */
export const ARCHIVE_FOLDER = "Archive"

/** One row on the platform page: a list the system has, or one the site expects and is missing. */
export type OverviewRow = {missing: MissingTarget; list?: undefined} | {list: ListedTarget; missing?: undefined}

/** A folder of rows as the page draws it; the lists that follow nothing and the archive are groups of their own. */
export type OverviewGroup = {name: string; kind: "folder" | "unlinked" | "archive"; rows: OverviewRow[]}

const plural = (count: number, one: string, many = `${one}s`) => `${count} ${count === 1 ? one : many}`

/** A list's drift from its newest reconcile, as a mark and the words on it. */
export function driftOf(list: ListedTarget): {kind: StateKind; word: string} {
  if (list.targetId == null) return {kind: "not-compared", word: "Not compared"}
  const missing = list.missing ?? 0
  const extra = list.extra ?? 0
  if (list.missing == null && list.extra == null) return {kind: "not-compared", word: "Not reconciled yet"}
  if (missing > 0 && extra > 0) return {kind: "missing", word: `${missing} missing, ${extra} additional`}
  if (missing > 0) return {kind: "missing", word: `${missing} missing`}
  if (extra > 0) return {kind: "extra", word: `${extra} additional`}
  return {kind: "in-sync", word: "In sync"}
}

/** The same drift as marks, one a line: missing and extra each get a mark of their own. */
export function driftMarksOf(list: ListedTarget): Array<{kind: StateKind; word: string}> {
  const missing = list.missing ?? 0
  const extra = list.extra ?? 0
  if (list.targetId == null || (list.missing == null && list.extra == null) || (missing === 0 && extra === 0)) return [driftOf(list)]
  return [
    ...(missing > 0 ? [{kind: "missing" as const, word: `${missing} missing`}] : []),
    ...(extra > 0 ? [{kind: "extra" as const, word: `${extra} additional`}] : []),
  ]
}

/** What fills a list: the kind of cohort and its name, or nothing for a list made by hand. */
export function followsOf(row: OverviewRow): string {
  const type = row.missing?.cohortType ?? row.list?.cohortType
  const label = row.missing?.cohortLabel ?? row.list?.cohortLabel
  return type && label ? `${cohortTypeLabel(type)} · ${label}` : "Nothing"
}

const matches = (needle: string, ...values: (string | null | undefined)[]) =>
  needle === "" || values.some((value) => value?.toLowerCase().includes(needle))

/**
 * The page's groups for a search: a folder holding a missing list first, then the folders by name,
 * then the lists that follow nothing, then the archive. A missing list leads its folder.
 */
export function groupsOf(overview: TargetOverview, search: string): OverviewGroup[] {
  const needle = search.trim().toLowerCase()
  const folders = new Map<string, OverviewRow[]>()
  const add = (name: string, row: OverviewRow) => folders.set(name, [...(folders.get(name) ?? []), row])
  for (const missing of overview.missing) {
    if (matches(needle, missing.cohortLabel, missing.folder)) add(missing.folder ?? "No folder", {missing})
  }
  const unlinked: OverviewRow[] = []
  const archived: OverviewRow[] = []
  const byLabel = [...overview.lists].sort((a, b) => a.label.localeCompare(b.label))
  for (const list of byLabel) {
    if (!matches(needle, list.label, list.folderLabel, list.cohortLabel)) continue
    if (list.folderLabel === ARCHIVE_FOLDER) archived.push({list})
    else if (list.targetId == null) unlinked.push({list})
    else add(list.folderLabel ?? "No folder", {list})
  }
  const named = [...folders.entries()]
    .sort(([a, rowsA], [b, rowsB]) => Number(rowsB.some((row) => row.missing)) - Number(rowsA.some((row) => row.missing)) || a.localeCompare(b))
    .map(([name, rows]): OverviewGroup => ({name, kind: "folder", rows}))
  return [
    ...named,
    ...(unlinked.length ? [{name: "Follows nothing", kind: "unlinked" as const, rows: unlinked}] : []),
    ...(archived.length ? [{name: ARCHIVE_FOLDER, kind: "archive" as const, rows: archived}] : []),
  ]
}

/** How many lists, how many drift and by how much, and when the newest reconcile ran. */
export function overviewFacts(overview: TargetOverview): Fact[] {
  const folders = new Set(overview.lists.map((list) => list.folderLabel ?? ""))
  const drifting = overview.lists.filter((list) => (list.missing ?? 0) + (list.extra ?? 0) > 0)
  const missing = drifting.reduce((sum, list) => sum + (list.missing ?? 0), 0)
  const extra = drifting.reduce((sum, list) => sum + (list.extra ?? 0), 0)
  return [
    {label: "Lists", value: String(overview.lists.length), sub: `in ${plural(folders.size, "folder")}`, testid: "brevo-fact-lists"},
    {label: "Drift", value: plural(drifting.length, "list"), sub: `${plural(missing, "person", "people")} missing, ${extra} additional`, testid: "brevo-fact-drift"},
    {
      label: "Last reconcile",
      value: overview.lastReconciledAt ? formatDateNoSeconds(overview.lastReconciledAt) : "Never",
      sub: "Nightly, and after every change",
      testid: "brevo-fact-reconciled",
    },
  ]
}

/** The notice over the page while lists the site expects are missing. */
export function missingNotice(missing: MissingTarget[]): {title: string; body: string} | null {
  if (missing.length === 0) return null
  const names = missing.map((one) => one.cohortLabel)
  const named = names.length === 1 ? names[0] : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`
  return {
    title: `${plural(missing.length, "list")} the site expects ${missing.length === 1 ? "is" : "are"} missing`,
    body: `${named} ${missing.length === 1 ? "has" : "have"} no list yet, so nobody in ${missing.length === 1 ? "it" : "them"} gets mail sent to a list. ` +
      "Creating one puts it in its folder and fills it straight away.",
  }
}

/** When the last tidy was applied, by whom and what it moved. */
export function lastTidyLine(last: LastTidy | null): string {
  if (!last) return "No tidy has been applied yet."
  const by = last.appliedByName ? ` by ${last.appliedByName}` : ""
  const failed = last.failed > 0 ? `, ${last.failed} refused` : ""
  return `Last applied ${formatDateNoSeconds(last.appliedAt)}${by}: ${plural(last.moved, "list")} moved${failed}.`
}
