#!/usr/bin/env node

import fs from "node:fs"
import path from "node:path"
import process from "node:process"
import istanbulCoverage from "istanbul-lib-coverage"
import istanbulReport from "istanbul-lib-report"
import istanbulReports from "istanbul-reports"

const {createCoverageMap} = istanbulCoverage
const {createContext} = istanbulReport

function parseArgs(argv) {
  const options = {
    rawDir: "api/build/coverage/frontend-system/raw",
    outDir: "api/build/coverage/frontend-system",
  }

  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === "--raw-dir" && argv[i + 1]) {
      options.rawDir = argv[++i]
      continue
    }
    if (arg === "--out-dir" && argv[i + 1]) {
      options.outDir = argv[++i]
      continue
    }
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: convert-frontend-coverage.mjs [--raw-dir <dir>] [--out-dir <dir>]")
      process.exit(0)
    }
    console.error(`Unknown argument: ${arg}`)
    process.exit(2)
  }

  return options
}

function readCoverageFiles(rawDir) {
  if (!fs.existsSync(rawDir)) {
    throw new Error(`Raw coverage directory does not exist: ${rawDir}`)
  }

  return fs
    .readdirSync(rawDir)
    .filter((name) => name.endsWith(".json"))
    .sort()
    .map((name) => path.join(rawDir, name))
}

function mergeCoverage(files) {
  const coverageMap = createCoverageMap({})

  for (const file of files) {
    const raw = fs.readFileSync(file, "utf8").trim()
    if (!raw) continue

    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== "object") continue

    coverageMap.merge(parsed)
  }

  return coverageMap
}

function writeReports(coverageMap, outDir) {
  fs.mkdirSync(outDir, {recursive: true})

  const context = createContext({
    dir: outDir,
    coverageMap,
    defaultSummarizer: "nested",
  })

  istanbulReports.create("lcovonly", {file: "lcov.info"}).execute(context)
  istanbulReports.create("html", {subdir: "html"}).execute(context)
  istanbulReports.create("json-summary", {file: "coverage-summary.json"}).execute(context)
}

function main() {
  const {rawDir, outDir} = parseArgs(process.argv)
  const files = readCoverageFiles(rawDir)

  if (files.length === 0) {
    throw new Error(`No raw coverage JSON files found in: ${rawDir}`)
  }

  const coverageMap = mergeCoverage(files)

  if (coverageMap.files().length === 0) {
    throw new Error(`Merged coverage map is empty. Raw files were found in: ${rawDir}`)
  }

  writeReports(coverageMap, outDir)
  console.log(`Merged ${files.length} frontend coverage file(s) into: ${outDir}`)
}

main()
