import {existsSync, readFileSync} from "node:fs"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

// Held in a variable, not written inline: Vite rewrites a literal `new URL("…", import.meta.url)`.
const FRONTEND_ROOT = "../../../"
const frontend = fileURLToPath(new URL(FRONTEND_ROOT, import.meta.url))

/** The files vitest.config.ts holds a per-file coverage floor for. */
function flooredFiles(): string[] {
  const config = readFileSync(`${frontend}vitest.config.ts`, "utf8")
  const thresholds = config.slice(config.indexOf("thresholds: {"))
  return [...thresholds.matchAll(/^\s+"(src\/[^"]+)": \{/gm)].map((match) => match[1])
}

describe("coverage floors", () => {
  // Vitest skips a floor whose file is gone, so one left behind looks like protection and is none.
  it("name only files that exist", () => {
    const floors = flooredFiles()

    expect(floors.length).toBeGreaterThan(0)
    expect(floors.filter((file) => !existsSync(`${frontend}${file}`))).toEqual([])
  })
})
