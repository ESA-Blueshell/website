import {readdirSync, statSync} from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

const thisDir = path.dirname(fileURLToPath(import.meta.url))
const srcRoot = path.resolve(thisDir, "../../src")

function walkFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const absolute = path.join(dir, entry)
    if (statSync(absolute).isDirectory()) {
      return walkFiles(absolute)
    }
    return [absolute]
  })
}

function toBrowserModulePath(absolutePath: string) {
  const relative = path.relative(srcRoot, absolutePath).split(path.sep).join("/")
  return `/src/${relative}`
}

function extractErrorMessage(value: unknown) {
  if (value == null) {
    return "unknown import error"
  }
  if (typeof value === "string") {
    return value
  }
  if (value instanceof Error) {
    return value.message
  }
  return String(value)
}

test.describe("frontend module smoke", () => {
  test("all page modules import with default exports", async ({page}) => {
    await installApiMocks(page)
    const pageRoot = path.join(srcRoot, "pages")
    const pageModulePaths = walkFiles(pageRoot)
      .filter((file) => file.endsWith(".vue"))
      .map(toBrowserModulePath)
      .sort()

    expect(pageModulePaths.length).toBeGreaterThan(0)
    await page.goto("/")

    const failures: string[] = []
    for (const modulePath of pageModulePaths) {
      const result = await page.evaluate(async (modulePathArg: string) => {
        try {
          const mod = await import(modulePathArg)
          return mod?.default ? "ok" : "missing-default-export"
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error)
          return `import-error: ${message}`
        }
      }, modulePath)

      if (result !== "ok") {
        failures.push(`${modulePath} -> ${extractErrorMessage(result)}`)
      }
    }

    expect(failures).toEqual([])
  })

  test("all non-generated source modules import without runtime errors", async ({page}) => {
    await installApiMocks(page)
    const modulePaths = walkFiles(srcRoot)
      .filter((file) => file.endsWith(".ts") || file.endsWith(".vue"))
      .filter((file) => !file.includes(`${path.sep}services${path.sep}api${path.sep}`))
      .filter((file) => !file.endsWith(".gen.ts"))
      .filter((file) => !file.endsWith(`${path.sep}main.ts`))
      .map(toBrowserModulePath)
      .sort()

    expect(modulePaths.length).toBeGreaterThan(0)
    await page.goto("/")

    const failures: string[] = []
    for (const modulePath of modulePaths) {
      const result = await page.evaluate(async (modulePathArg: string) => {
        try {
          await import(modulePathArg)
          return "ok"
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error)
          return `import-error: ${message}`
        }
      }, modulePath)

      if (result !== "ok") {
        failures.push(`${modulePath} -> ${extractErrorMessage(result)}`)
      }
    }

    expect(failures).toEqual([])
  })
})
