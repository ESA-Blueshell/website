import {readdirSync, readFileSync} from "node:fs"
import {matchesGlob} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

// Held in a variable, not written inline: Vite rewrites a literal `new URL("…", import.meta.url)`.
const FRONTEND_ROOT = "../../../"
const frontend = fileURLToPath(new URL(FRONTEND_ROOT, import.meta.url))

/** The files and globs vitest.config.ts holds a per-file coverage floor for. */
function flooredFiles(): string[] {
  const config = readFileSync(`${frontend}vitest.config.ts`, "utf8")
  const thresholds = config.slice(config.indexOf("thresholds: {"))
  return [...thresholds.matchAll(/^\s+"(src\/[^"]+)": \{/gm)].map((match) => match[1])
}

/** Every source file, named the way a floor names it. */
function sourceFiles(): string[] {
  return readdirSync(`${frontend}src`, {recursive: true, encoding: "utf8"}).map((file) => `src/${file}`)
}

describe("coverage floors", () => {
  // Vitest skips a floor whose files are gone, so one left behind looks like protection and is none.
  it("name only files that exist, and globs that match one", () => {
    const files = sourceFiles()
    const floors = flooredFiles()

    expect(floors.length).toBeGreaterThan(0)
    expect(floors.filter((floor) => !files.some((file) => matchesGlob(file, floor)))).toEqual([])
  })
})
