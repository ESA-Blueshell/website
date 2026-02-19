#!/usr/bin/env node

import fs from "node:fs"
import path from "node:path"
import process from "node:process"
import istanbulCoverage from "istanbul-lib-coverage"
import istanbulReport from "istanbul-lib-report"
import istanbulReports from "istanbul-reports"

const {createCoverageMap} = istanbulCoverage
const {createContext} = istanbulReport

const DEFAULT_BACKEND_PREFIX = "api/src/main/kotlin/net/blueshell/api"
const BACKEND_PACKAGE_PREFIX = "net/blueshell/api"

function parseArgs(argv) {
  const options = {
    jacocoFiles: [],
    frontendJson: "",
    outDir: "",
    backendPrefix: DEFAULT_BACKEND_PREFIX,
  }

  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === "--jacoco" && argv[i + 1]) {
      options.jacocoFiles = argv[++i].split(";").filter(Boolean)
      continue
    }
    if (arg === "--frontend-json" && argv[i + 1]) {
      options.frontendJson = argv[++i]
      continue
    }
    if ((arg === "--out" || arg === "--out-dir") && argv[i + 1]) {
      options.outDir = argv[++i]
      continue
    }
    if (arg === "--backend-prefix" && argv[i + 1]) {
      options.backendPrefix = argv[++i]
      continue
    }
    if (arg === "-h" || arg === "--help") {
      console.log(
        "Usage: merge-system-coverage.mjs --jacoco <a.xml;b.xml> --frontend-json <coverage-final.json> --out <dir> [--backend-prefix <prefix>]"
      )
      process.exit(0)
    }
    throw new Error(`Unknown argument: ${arg}`)
  }

  if (options.jacocoFiles.length === 0 || !options.frontendJson || !options.outDir) {
    throw new Error("Missing required arguments. Use --help for usage.")
  }

  return options
}

function normalizePathToPosix(filePath) {
  return filePath.replaceAll("\\", "/")
}

function resolveBackendSourcePath(packageName, sourceName, repoRoot) {
  const packagePath = packageName ? `${packageName}/` : ""
  const kotlinRelative = `api/src/main/kotlin/${packagePath}${sourceName}`
  const javaRelative = `api/src/main/java/${packagePath}${sourceName}`
  const kotlinAbsolute = path.resolve(repoRoot, kotlinRelative)
  const javaAbsolute = path.resolve(repoRoot, javaRelative)

  if (fs.existsSync(kotlinAbsolute)) return normalizePathToPosix(kotlinRelative)
  if (fs.existsSync(javaAbsolute)) return normalizePathToPosix(javaRelative)
  return normalizePathToPosix(kotlinRelative)
}

function addLineHit(lineHitsByFile, filePath, lineNumber, hits) {
  if (!lineHitsByFile.has(filePath)) {
    lineHitsByFile.set(filePath, new Map())
  }
  const lineHits = lineHitsByFile.get(filePath)
  const existing = lineHits.get(lineNumber) ?? 0
  lineHits.set(lineNumber, Math.max(existing, hits))
}

function collectBackendLineHitsFromJacoco(jacocoPath, lineHitsByFile, repoRoot) {
  const xml = fs.readFileSync(jacocoPath, "utf8")
  const packageRegex = /<package name="([^"]*)">([\s\S]*?)<\/package>/g
  const sourceFileRegex = /<sourcefile name="([^"]+)">([\s\S]*?)<\/sourcefile>/g
  const lineRegex = /<line nr="(\d+)" mi="(\d+)" ci="(\d+)" mb="(\d+)" cb="(\d+)"\/>/g

  let packageMatch
  while ((packageMatch = packageRegex.exec(xml)) !== null) {
    const packageName = packageMatch[1]
    if (!packageName.startsWith(BACKEND_PACKAGE_PREFIX)) {
      continue
    }

    const packageBody = packageMatch[2]

    let sourceMatch
    while ((sourceMatch = sourceFileRegex.exec(packageBody)) !== null) {
      const sourceName = sourceMatch[1]
      const sourceBody = sourceMatch[2]
      const filePath = resolveBackendSourcePath(packageName, sourceName, repoRoot)

      let lineMatch
      while ((lineMatch = lineRegex.exec(sourceBody)) !== null) {
        const lineNumber = Number.parseInt(lineMatch[1], 10)
        const coveredInstructions = Number.parseInt(lineMatch[3], 10)
        const hits = coveredInstructions > 0 ? 1 : 0
        if (!Number.isNaN(lineNumber)) {
          addLineHit(lineHitsByFile, filePath, lineNumber, hits)
        }
      }
    }
  }
}

function createIstanbulFileCoverage(filePath, lineHits) {
  const statementMap = {}
  const statements = {}
  let statementId = 0

  for (const [lineNumber, hits] of Array.from(lineHits.entries()).sort((a, b) => a[0] - b[0])) {
    const id = String(statementId++)
    statementMap[id] = {
      start: {line: lineNumber, column: 0},
      end: {line: lineNumber, column: 0},
    }
    statements[id] = hits
  }

  return {
    path: filePath,
    statementMap,
    s: statements,
    fnMap: {},
    f: {},
    branchMap: {},
    b: {},
  }
}

function normalizeFrontendFilePath(filePath) {
  const normalized = normalizePathToPosix(filePath)

  if (normalized.startsWith("frontend/")) {
    return normalized
  }
  if (normalized.startsWith("src/")) {
    return `frontend/${normalized}`
  }
  if (normalized.includes("/frontend/")) {
    return normalized.slice(normalized.indexOf("/frontend/") + 1)
  }
  return normalized
}

function loadFrontendCoverage(frontendJsonPath) {
  const raw = JSON.parse(fs.readFileSync(frontendJsonPath, "utf8"))
  const inputMap = createCoverageMap(raw)
  const normalizedMap = createCoverageMap({})

  for (const filePath of inputMap.files()) {
    const fileCoverage = inputMap.fileCoverageFor(filePath).toJSON()
    fileCoverage.path = normalizeFrontendFilePath(fileCoverage.path ?? filePath)
    normalizedMap.addFileCoverage(fileCoverage)
  }

  return normalizedMap
}

function filterMergedCoverage(coverageMap, backendPrefix) {
  const normalizedBackendPrefix = normalizePathToPosix(backendPrefix).replace(/\/+$/, "")
  const filtered = createCoverageMap({})

  for (const filePath of coverageMap.files()) {
    const normalizedPath = normalizePathToPosix(filePath)
    const isFrontend = normalizedPath.startsWith("frontend/")
    const isBackend = normalizedPath === normalizedBackendPrefix || normalizedPath.startsWith(`${normalizedBackendPrefix}/`)
    if (!isFrontend && !isBackend) {
      continue
    }

    const fileCoverage = coverageMap.fileCoverageFor(filePath).toJSON()
    fileCoverage.path = normalizedPath
    filtered.addFileCoverage(fileCoverage)
  }

  return filtered
}

function writeReports(coverageMap, outDir) {
  fs.rmSync(outDir, {recursive: true, force: true})
  fs.mkdirSync(outDir, {recursive: true})

  const context = createContext({
    dir: outDir,
    coverageMap,
    defaultSummarizer: "nested",
  })

  istanbulReports.create("lcovonly", {file: "lcov.info"}).execute(context)
  istanbulReports.create("html", {subdir: "html"}).execute(context)
  istanbulReports.create("cobertura", {file: "Cobertura.xml"}).execute(context)
  istanbulReports.create("json", {file: "coverage-final.json"}).execute(context)
  istanbulReports.create("json-summary", {file: "coverage-summary.json"}).execute(context)

  const htmlIndex = path.join(outDir, "html", "index.html")
  if (fs.existsSync(htmlIndex)) {
    fs.copyFileSync(htmlIndex, path.join(outDir, "index.html"))
  }
}

function main() {
  const {jacocoFiles, frontendJson, outDir, backendPrefix} = parseArgs(process.argv)

  for (const jacocoFile of jacocoFiles) {
    if (!fs.existsSync(jacocoFile)) {
      throw new Error(`Missing JaCoCo report: ${jacocoFile}`)
    }
  }
  if (!fs.existsSync(frontendJson)) {
    throw new Error(`Missing frontend coverage JSON: ${frontendJson}`)
  }

  const repoRoot = path.basename(process.cwd()) === "frontend"
    ? path.resolve(process.cwd(), "..")
    : process.cwd()

  const mergedCoverage = createCoverageMap({})
  mergedCoverage.merge(loadFrontendCoverage(frontendJson))

  const lineHitsByFile = new Map()
  for (const jacocoFile of jacocoFiles) {
    collectBackendLineHitsFromJacoco(jacocoFile, lineHitsByFile, repoRoot)
  }

  for (const [filePath, lineHits] of lineHitsByFile.entries()) {
    mergedCoverage.addFileCoverage(createIstanbulFileCoverage(filePath, lineHits))
  }

  const filteredCoverage = filterMergedCoverage(mergedCoverage, backendPrefix)
  if (filteredCoverage.files().length === 0) {
    throw new Error("Merged coverage is empty after filtering.")
  }

  writeReports(filteredCoverage, outDir)
  console.log(
    `Merged coverage generated (${filteredCoverage.files().length} files):\n` +
      `- LCOV: ${path.join(outDir, "lcov.info")}\n` +
      `- Cobertura: ${path.join(outDir, "Cobertura.xml")}\n` +
      `- HTML: ${path.join(outDir, "html")}`
  )
}

main()
