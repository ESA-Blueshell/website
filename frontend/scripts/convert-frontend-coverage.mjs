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

  const files = []
  const queue = [rawDir]
  while (queue.length > 0) {
    const current = queue.pop()
    for (const entry of fs.readdirSync(current, {withFileTypes: true})) {
      const absolutePath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        queue.push(absolutePath)
        continue
      }
      if (entry.isFile() && entry.name.endsWith(".json")) {
        files.push(absolutePath)
      }
    }
  }

  return files.sort()
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

function normalizeSourcePath(filePath, repoRoot) {
  const normalized = filePath.replaceAll("\\", "/")
  const repoRootPosix = repoRoot.replaceAll("\\", "/")
  const frontendRootPosix = `${repoRootPosix}/frontend`

  const containerPrefixes = [
    "/usr/app/",
    "/workspace/frontend/",
  ]
  for (const prefix of containerPrefixes) {
    if (normalized.startsWith(prefix)) {
      return `frontend/${normalized.slice(prefix.length)}`
    }
  }

  const relativeContainerMatch = normalized.match(/^(\.\.\/)+usr\/app\/(.+)$/)
  if (relativeContainerMatch) {
    return `frontend/${relativeContainerMatch[2]}`
  }

  if (normalized.startsWith(`${frontendRootPosix}/`)) {
    return `frontend/${normalized.slice(frontendRootPosix.length + 1)}`
  }

  if (normalized.startsWith("src/")) {
    return `frontend/${normalized}`
  }

  const absolute = path.isAbsolute(filePath) ? filePath : path.resolve(filePath)
  const relative = path.relative(repoRoot, absolute).replaceAll("\\", "/")
  if (!relative.startsWith("..") && relative.length > 0) {
    return relative
  }

  return normalized
}

function normalizeCoverageMapPaths(coverageMap) {
  const cwd = process.cwd()
  const repoRoot = path.basename(cwd) === "frontend" ? path.resolve(cwd, "..") : cwd
  const normalizedMap = createCoverageMap({})

  for (const filePath of coverageMap.files()) {
    const json = coverageMap.fileCoverageFor(filePath).toJSON()
    json.path = normalizeSourcePath(json.path ?? filePath, repoRoot)
    normalizedMap.addFileCoverage(json)
  }

  return normalizedMap
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
  istanbulReports.create("json", {file: "coverage-final.json"}).execute(context)
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

  const normalizedCoverageMap = normalizeCoverageMapPaths(coverageMap)
  writeReports(normalizedCoverageMap, outDir)
  console.log(`Merged ${files.length} frontend coverage file(s) into: ${outDir}`)
}

main()
