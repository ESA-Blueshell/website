import {randomUUID} from "node:crypto"
import fs from "node:fs/promises"
import path from "node:path"
import process from "node:process"
import type {BrowserContext, TestInfo} from "@playwright/test"

export const coverageEnabled = process.env.FRONTEND_E2E_COVERAGE !== "false"
export const rawCoverageDir = path.resolve(
  process.cwd(),
  process.env.FRONTEND_E2E_COVERAGE_RAW_DIR ?? "coverage/e2e/raw",
)

function hasCoverageData(value: unknown): value is Record<string, unknown> {
  return (
    value != null &&
    typeof value === "object" &&
    Object.keys(value as Record<string, unknown>).length > 0
  )
}

function sanitizeName(input: string): string {
  return input.replace(/[^a-zA-Z0-9_-]/g, "_")
}

async function capturePageCoverage(page: import("@playwright/test").Page) {
  if (page.isClosed()) {
    return null
  }
  const coverage = await page
    .evaluate(() => (globalThis as {__coverage__?: unknown}).__coverage__ ?? null)
    .catch(() => null)
  if (!hasCoverageData(coverage)) {
    return null
  }
  return coverage
}

async function writeCoverageArtifact(testInfo: TestInfo, coverage: Record<string, unknown>) {
  await fs.mkdir(rawCoverageDir, {recursive: true})
  const title = sanitizeName(testInfo.titlePath.slice(1).join("-")).slice(0, 80)
  const fileName = `${Date.now()}-${sanitizeName(testInfo.project.name)}-${title}-${randomUUID()}.json`
  await fs.writeFile(path.join(rawCoverageDir, fileName), JSON.stringify(coverage), "utf8")
}

export async function collectContextCoverage(context: BrowserContext, testInfo: TestInfo): Promise<number> {
  if (!coverageEnabled) {
    return 0
  }

  let artifactsWritten = 0
  for (const page of context.pages()) {
    const coverage = await capturePageCoverage(page)
    if (coverage == null) {
      continue
    }
    await writeCoverageArtifact(testInfo, coverage)
    artifactsWritten += 1
  }

  return artifactsWritten
}
