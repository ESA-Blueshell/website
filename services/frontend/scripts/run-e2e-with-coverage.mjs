#!/usr/bin/env node

import {spawnSync} from "node:child_process"
import {existsSync, readdirSync, rmSync} from "node:fs"
import process from "node:process"

const coverageDir = "coverage/e2e"
const rawCoverageDir = `${coverageDir}/raw`
// CI shards the suite, so each slice only ever holds part of the picture: it
// uploads its raw dir and a fan-in job merges the six into one report. Anything
// but a shard still reports for itself.
const report = !process.argv.includes("--no-report")
const extraArgs = process.argv.slice(2).filter((arg) => arg !== "--no-report")

rmSync(coverageDir, {recursive: true, force: true})

const e2eResult = spawnSync(
  "yarn",
  ["playwright", "test", ...extraArgs],
  {
    stdio: "inherit",
    env: {
      ...process.env,
      VITE_COVERAGE: "true",
      FRONTEND_E2E_COVERAGE: "true",
      FRONTEND_E2E_COVERAGE_RAW_DIR: rawCoverageDir,
    },
  },
)

const hasRawCoverage = existsSync(rawCoverageDir) && readdirSync(rawCoverageDir).some((file) => file.endsWith(".json"))

if (hasRawCoverage && report) {
  const reportResult = spawnSync(
    "yarn",
    [
      "node",
      "./scripts/convert-frontend-coverage.mjs",
      "--raw-dir",
      rawCoverageDir,
      "--out-dir",
      coverageDir,
    ],
    {
      stdio: "inherit",
    },
  )
  if (reportResult.status !== 0 && e2eResult.status === 0) {
    process.exit(reportResult.status ?? 1)
  }
} else if (!hasRawCoverage) {
  const message = `No raw coverage was captured in ${rawCoverageDir}`
  if (e2eResult.status === 0) {
    console.error(message)
    process.exit(1)
  }
  console.warn(message)
}

process.exit(e2eResult.status ?? 1)
