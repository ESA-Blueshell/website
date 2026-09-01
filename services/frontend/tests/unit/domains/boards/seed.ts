import {existsSync, readFileSync} from "node:fs"
import {dirname, join} from "node:path"
import {cwd} from "node:process"

/**
 * The board history as the api seeds it, read straight out of the csv files that record it.
 *
 * Read rather than transcribed because the ambiguity these rules exist for lives in the real
 * strings — two boards wrote their commissioners in lower case, four roles name two offices at
 * once, and one board wrote "Esports affairs" where the board before it wrote "Esports Affairs".
 * A copy of the list in a test would agree with itself and drift from the seed; this way a role
 * nobody has ranked yet fails the suite the moment it is seeded.
 */
const SEED = join("services", "api", "src", "main", "resources", "db", "seed", "boards")

/**
 * Where the seed is, found by walking up from wherever the suite was started.
 *
 * Walked rather than reached for relatively, because the suite runs from the frontend and from
 * the repository root, and `import.meta.url` inside a test is not a file at all.
 */
function seedDirectory(): string {
  let at = cwd()
  for (;;) {
    if (existsSync(join(at, SEED))) return join(at, SEED)
    const up = dirname(at)
    if (up === at) throw new Error(`the board seed (${SEED}) is not under ${cwd()} or above it`)
    at = up
  }
}

/** Fields, quote-aware: a description holds commas, quotes and the odd newline. */
function fields(text: string): string[][] {
  const records: string[][] = [[]]
  let field = ""
  let quoted = false
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    if (quoted) {
      if (char !== '"') field += char
      else if (text[i + 1] === '"') {
        field += '"'
        i += 1
      } else quoted = false
      continue
    }
    if (char === '"') quoted = true
    else if (char === ",") {
      records[records.length - 1]!.push(field)
      field = ""
    } else if (char === "\n") {
      records[records.length - 1]!.push(field)
      field = ""
      records.push([])
    } else if (char !== "\r") field += char
  }
  records[records.length - 1]!.push(field)
  return records.filter(record => record.some(one => one !== ""))
}

function rows(file: string): Record<string, string>[] {
  const [header, ...records] = fields(readFileSync(join(seedDirectory(), file), "utf8"))
  if (!header) throw new Error(`${file} in the board seed is empty`)
  return records.map(record => Object.fromEntries(header.map((name, at) => [name, record[at] ?? ""])))
}

/** Every board recorded, in the order the file records them. */
export function seededBoards(): {number: number; name: string; startDate: string; endDate: string}[] {
  return rows("boards.csv").map(row => ({
    number: Number(row.number),
    name: row.name ?? "",
    startDate: row.start_date ?? "",
    endDate: row.end_date ?? "",
  }))
}

/** Every seat recorded, in the order the file records them. */
export function seededSeats(): {board: number; name: string; role: string}[] {
  return rows("seats.csv").map(row => ({
    board: Number(row.board),
    name: row.name ?? "",
    role: row.role ?? "",
  }))
}

/** Every distinct role string the association has written down, alphabetically. */
export function seededRoles(): string[] {
  return [...new Set(seededSeats().map(seat => seat.role))].sort()
}
