/**
 * Finds generated-sdk writes whose outcome nothing looks at.
 *
 * A generated call without `throwOnError: true` resolves on 4xx and 5xx — see
 * `services/frontend/src/services/api/blueshell/client/client.gen.ts`, whose catch returns the
 * error object — so a surrounding `try`/`catch` is dead code and the caller carries on as though
 * the write landed. This is the text scan behind that rule; `uncheckedSdkWrites.test.ts` is the
 * rule itself, and pins the call sites that offend today.
 */

/** One place a mutating sdk function is called, by the line the call opens on. */
export interface WriteCallSite {
  fn: string
  line: number
}

/**
 * The generated functions that send POST, PUT, PATCH or DELETE.
 *
 * Read out of `sdk.gen.ts` rather than listed here, so a newly generated write is covered the
 * moment the spec grows one. Reads are #1014's problem and are left alone.
 */
export function mutatingSdkFunctions(sdkSource: string): Set<string> {
  const declaration = /export const (\w+) = <ThrowOnError[\s\S]*?\)\.(get|post|put|patch|delete)</g
  const mutators = new Set<string>()
  for (const [, name, method] of sdkSource.matchAll(declaration)) {
    if (method !== "get") mutators.add(name)
  }
  return mutators
}

const OPENERS = "([{"
const CLOSERS = ")]}"

/** Comments described the trap long before this scan did, so they must not read as call sites. */
function withoutComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, block => block.replace(/[^\n]/g, " "))
    .split("\n")
    .map(line => (/^\s*(\/\/|\*)/.test(line) ? "" : line))
    .join("\n")
}

/** The index just past the call's argument list, or -1 when the parentheses never close. */
function endOfArguments(source: string, open: number): number {
  let depth = 0
  for (let at = open; at < source.length; at++) {
    if (OPENERS.includes(source[at])) depth++
    else if (CLOSERS.includes(source[at])) {
      depth--
      if (depth === 0) return at + 1
    }
  }
  return -1
}

/** A line that opens with one of these carries on from the line above, so the two are one. */
const CONTINUATION = /^\s*(?:[?:,+=]|&&|\|\||\?\?)?\s*$|^\s*(?:[?:,+]|&&|\|\||\?\?)/

/**
 * The statement text leading up to the call: what receives its result.
 *
 * Bracketed groups are stepped over so a destructuring pattern's own braces do not read as the
 * end of the statement, and a line that only carries on from the one above it folds into it —
 * this codebase writes no semicolons, and a save is often a ternary between a create and an
 * update spread over three lines.
 */
function receivingPrefix(source: string, callStart: number): string {
  let at = callStart - 1
  let depth = 0
  let folded = 0
  while (at >= 0) {
    const char = source[at]
    if (CLOSERS.includes(char)) depth++
    else if (OPENERS.includes(char)) {
      if (depth === 0) break
      depth--
    } else if (depth === 0 && (char === ";" || char === "\n")) {
      const sofar = source.slice(at + 1, callStart)
      if (folded >= 4 || !CONTINUATION.test(sofar.split("\n")[0])) break
      folded++
    }
    at--
  }
  return source.slice(at + 1, callStart)
}

/**
 * The rest of the block the call was made in, cut short where the binding is reassigned.
 *
 * A binding only tells us anything for as long as it holds this call's result, and names like
 * `res` recur, so a check further down the file is not evidence about this call.
 */
function scopeAfter(source: string, from: number, binding: string): string {
  let depth = 0
  let at = from
  for (; at < source.length; at++) {
    const char = source[at]
    if (OPENERS.includes(char)) depth++
    else if (CLOSERS.includes(char)) {
      if (depth === 0) break
      depth--
    }
  }
  const window = source.slice(from, at)
  const name = escaped(binding)
  const rebound = window.search(new RegExp(`(?:const|let|var)\\s+${name}\\b|\\b${name}\\s*=[^=]`))
  return rebound === -1 ? window : window.slice(0, rebound)
}

const BINDING = /(?:const|let|var)\s+(\{[^}]*\}|[\w$]+)\s*=[^=][\s\S]*$/
const PROPAGATED = /\breturn\b[^\n]*$|=>\s*$/
const INSPECTED = /^\s*\)*\s*\??\.\s*(?:error|data)\b/

const escaped = (name: string) => name.replace(/\$/g, "\\$")

/** A result is inspected when the shape of the refusal — the error, or a missing body — is read. */
function readsOutcome(text: string, binding: string): boolean {
  const reads = `\\b${escaped(binding)}\\s*\\??\\.\\s*(?:error|data|status|response)\\b`
  return new RegExp(reads).test(text)
}

/**
 * The names a file pulls in from the generated client, under the names it calls them by.
 *
 * Scoped to the api module on purpose: `apply` and `retry` are generated write functions and also
 * ordinary local helpers, so a bare name is not enough to tell one from the other.
 */
export function sdkNamesImported(source: string): Set<string> {
  const clauses = withoutComments(source)
    .matchAll(/import\s+(?:type\s+)?\{([^}]*)\}\s*from\s*["']([^"']+)["']/g)
  return new Set(
    [...clauses]
      .filter(match => /(^|\/)services\/api(\/|$)/.test(match[2]))
      .flatMap(match =>
        match[1].split(",").map(one => one.trim().split(/\s+as\s+/).pop()!.trim())),
  )
}

/** The mutating calls in one file whose refusal would go unnoticed. */
export function findUncheckedWrites(source: string, mutators: Set<string>): WriteCallSite[] {
  const text = withoutComments(source)
  const found: WriteCallSite[] = []

  for (const match of text.matchAll(/([\w$]+)\s*\(/g)) {
    const name = match[1]
    if (!mutators.has(name)) continue
    const start = match.index
    if (start > 0 && /[.\w$]/.test(text[start - 1])) continue

    const open = start + match[0].length - 1
    const end = endOfArguments(text, open)
    if (end === -1) continue

    const args = text.slice(open, end)
    if (/throwOnError\s*:\s*true/.test(args)) continue
    if (INSPECTED.test(text.slice(end))) continue

    const prefix = receivingPrefix(text, start)
    if (PROPAGATED.test(prefix)) continue

    const binding = prefix.match(BINDING)?.[1]
    const checked = binding?.startsWith("{")
      ? /\b(error|data)\b/.test(binding)
      : binding != null && readsOutcome(scopeAfter(text, end, binding), binding)
    if (checked) continue

    found.push({fn: name, line: text.slice(0, start).split("\n").length})
  }

  return found
}
