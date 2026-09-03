/**
 * A mutating sdk call whose refusal nothing reads fails this test.
 *
 * The generated client resolves rather than throws on 4xx and 5xx unless the call passes
 * `throwOnError: true`, so a `try`/`catch` around a bare call is dead code and the caller carries
 * on reporting a write that never landed. Every offender in #1010 has that one shape, so the guard
 * is the shape rather than the twenty symptoms.
 *
 * A test rather than an eslint rule, for two reasons. The set of mutating functions is read out of
 * `sdk.gen.ts`, so a write added to the api spec is covered without anyone editing a list; and the
 * pinned baseline below has to be checked for staleness, which needs a view of the whole sweep at
 * once — eslint sees one file at a time and has nowhere to report a pin that is no longer needed.
 * `domains/boards/adapters/boards.ts` remains the shape a call site should have; this only says
 * that a call site must have made *some* arrangement to notice a refusal.
 *
 * The ratchet follows `SharedFanInArchitectureTest` on the api side: what offends today is pinned
 * with the ticket that removes it, so the rule bites everywhere else and for everything added
 * later. Pins are source in this file, so growing the list is a visible edit in review; and a pin
 * that stops offending fails the second test, so the list cannot rot into a permanent exemption.
 */
import {readdirSync, readFileSync} from "node:fs"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"
import {findUncheckedWrites, mutatingSdkFunctions, sdkNamesImported} from "./sdkWriteScan"

// Held in a variable, not written inline: Vite rewrites a literal `new URL("…", import.meta.url)`
// into an asset reference, and the path never reaches node:url.
const FRONTEND_ROOT = "../../../"
const frontend = fileURLToPath(new URL(FRONTEND_ROOT, import.meta.url))

/** Reading every source file takes well under a second, and far longer on a contended runner. */
const SWEEP_TIMEOUT_MS = 30_000

/**
 * The call sites that offend today, by file and generated function, against the ticket that
 * removes each one. Line numbers are deliberately not pinned: an edit further up a file would
 * then fail a build that changed nothing about the refusal.
 */
const PINNED: Record<string, Record<string, string>> = {
  // A refused deletion still reads `Deleted "<title>"`, and the event leaves the list and the
  // calendar. Two sibling handlers in the same file already pass `throwOnError: true`.
  "src/components/common/cards/EventCard.vue": {deleteEventById: "#1012"},

  // No result check and no catch: the period disappears from the page and returns on the next load.
  "src/components/common/lists/ContributionPeriodList.vue": {deleteContributionPeriodById: "#1012"},
  "src/components/common/rows/AddressUserRow.vue": {deleteAddressById: "#1012"},
  "src/pages/management/CommitteeManager.vue": {deleteCommitteeById: "#1012"},
  "src/pages/management/UserManager.vue": {deleteUserById: "#1012"},

  // The optimistic flip's rollback sits in the dead catch and nothing refetches, so a refused
  // toggle leaves a member green and the books close on a payment that was never recorded.
  "src/composables/usePaidToggle.ts": {createContribution: "#1012", deleteContribution: "#1012"},

  // The success screen is set in a `finally`, so a 500 also promises an email is on the way.
  "src/pages/login/ForgotPassword.vue": {resetPassword: "#1012"},

  // A refused retry tells the operator nothing.
  "src/pages/management/EmailManager.vue": {retry1: "#1012"},

  // The 409-conflict branch below the call is unreachable, so linking an id another user owns
  // reports "External id linked" and leaves the row unlinked.
  "src/domains/cohorts/adapters/cohorts.ts": {linkUser: "#1013"},

  // Returns `void`, so a refused handle removal is silent all the way up.
  "src/domains/esports/adapters/esports.ts": {clearGameAccount: "#1013"},
}

/** Every `.ts` and `.vue` file under a directory, as paths relative to the frontend root. */
function sourcesUnder(directory: string): string[] {
  return readdirSync(`${frontend}${directory}`, {withFileTypes: true}).flatMap(entry => {
    const path = `${directory}/${entry.name}`
    if (entry.isDirectory()) return sourcesUnder(path)
    return /\.(ts|vue)$/.test(entry.name) ? [path] : []
  })
}

const mutators = () =>
  mutatingSdkFunctions(readFileSync(`${frontend}src/services/api/blueshell/sdk.gen.ts`, "utf8"))

let swept: Map<string, Set<string>> | null = null

/**
 * Every unchecked write in `src`, keyed the way [PINNED] is. The generated client is exempt.
 *
 * Read once and kept: this reads every source file, and all three rules below ask the same
 * question of the same tree.
 */
function sweep(): Map<string, Set<string>> {
  if (swept) return swept
  const writes = mutators()
  const found = new Map<string, Set<string>>()

  for (const file of sourcesUnder("src").filter(one => !one.startsWith("src/services/api/"))) {
    const source = readFileSync(`${frontend}${file}`, "utf8")
    const imported = sdkNamesImported(source)
    const inScope = new Set([...writes].filter(one => imported.has(one)))
    if (inScope.size === 0) continue

    for (const call of findUncheckedWrites(source, inScope)) {
      if (!found.has(file)) found.set(file, new Set())
      found.get(file)!.add(call.fn)
    }
  }

  swept = found
  return found
}

const flatten = (calls: Map<string, Set<string>>): string[] =>
  [...calls].flatMap(([file, fns]) => [...fns].map(fn => `${file} ${fn}`)).sort()

const pinnedCalls = (): string[] =>
  Object.entries(PINNED).flatMap(([file, fns]) => Object.keys(fns).map(fn => `${file} ${fn}`)).sort()

describe("unchecked sdk writes", () => {
  it("finds the generated writes to look for at all", () => {
    const writes = mutators()

    // A generator change that broke the scan would otherwise pass this file silently.
    expect(writes.size).toBeGreaterThan(50)
    expect(writes.has("deleteBoard")).toBe(true)
    expect(writes.has("findAllBoards")).toBe(false)
  })

  it("every mutating sdk call arranges to notice a refusal", () => {
    const offenders = flatten(sweep()).filter(one => !pinnedCalls().includes(one))

    expect(
      offenders,
      "a generated write resolves on 4xx and 5xx, so a try/catch around it is dead code. Pass " +
        "throwOnError: true, or read res.error / res.data and report the refusal — see " +
        "domains/boards/adapters/boards.ts. Pinning it in PINNED is the last resort and needs a " +
        "ticket",
    ).toEqual([])
  }, SWEEP_TIMEOUT_MS)

  it("no pinned call site stays pinned once it checks its refusal", () => {
    const offending = flatten(sweep())
    const stale = pinnedCalls().filter(one => !offending.includes(one))

    expect(
      stale,
      "these call sites now check their refusal — drop them from PINNED so the ratchet cannot slip",
    ).toEqual([])
  }, SWEEP_TIMEOUT_MS)

  it("every pinned file still exists", () => {
    const present = new Set(sourcesUnder("src"))
    const vanished = Object.keys(PINNED).filter(file => !present.has(file)).sort()

    expect(vanished, "these files are gone — drop them from PINNED").toEqual([])
  }, SWEEP_TIMEOUT_MS)
})
