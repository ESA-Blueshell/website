#!/usr/bin/env node

import fs from "node:fs"
import path from "node:path"
import process from "node:process"
import {XMLParser} from "fast-xml-parser"
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
    frontendJsonFiles: [],
    outDir: "",
  }

  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === "--jacoco" && argv[i + 1]) {
      options.jacocoFiles = argv[++i].split(";").filter(Boolean)
      continue
    }
    if ((arg === "--frontend-json" || arg === "--frontend-jsons") && argv[i + 1]) {
      const values = argv[++i].split(";").map((value) => value.trim()).filter(Boolean)
      options.frontendJsonFiles.push(...values)
      continue
    }
    if ((arg === "--out" || arg === "--out-dir") && argv[i + 1]) {
      options.outDir = argv[++i]
      continue
    }
    if (arg === "-h" || arg === "--help") {
      console.log(
        "Usage: merge-system-coverage.mjs --jacoco <a.xml;b.xml> --frontend-json <sys.json;frontend-tests.json> --out <dir>"
      )
      process.exit(0)
    }
    throw new Error(`Unknown argument: ${arg}`)
  }

  if (options.jacocoFiles.length === 0 || options.frontendJsonFiles.length === 0 || !options.outDir) {
    throw new Error("Missing required arguments. Use --help for usage.")
  }

  options.frontendJsonFiles = Array.from(new Set(options.frontendJsonFiles))

  return options
}

function normalizePathToPosix(filePath) {
  return filePath.replaceAll("\\", "/")
}

function stripLeadingDotSlash(filePath) {
  return filePath.replace(/^\.\/+/, "")
}

function toRepoRelativeFromAbsolute(absolutePath, repoRoot) {
  const normalizedAbsolute = path.resolve(absolutePath)
  const relativePath = normalizePathToPosix(path.relative(repoRoot, normalizedAbsolute))
  if (!relativePath || relativePath === ".") {
    return ""
  }
  if (relativePath === ".." || relativePath.startsWith("../")) {
    return null
  }
  return stripLeadingDotSlash(relativePath)
}

function normalizeCoveragePath(filePath, repoRoot) {
  const normalizedInput = normalizePathToPosix(filePath)

  if (path.isAbsolute(filePath)) {
    return toRepoRelativeFromAbsolute(filePath, repoRoot) ?? normalizedInput
  }

  const repoCandidate = path.resolve(repoRoot, normalizedInput)
  const repoRelative = toRepoRelativeFromAbsolute(repoCandidate, repoRoot)
  if (repoRelative) {
    return repoRelative
  }

  const frontendCandidate = path.resolve(repoRoot, "frontend", normalizedInput)
  const frontendRelative = toRepoRelativeFromAbsolute(frontendCandidate, repoRoot)
  if (frontendRelative) {
    return frontendRelative
  }

  return stripLeadingDotSlash(normalizedInput)
}

function resolveBackendSourcePath(packageName, sourceName, repoRoot) {
  const packagePath = packageName ? `${packageName}/` : ""
  const kotlinRelative = `api/src/main/kotlin/${packagePath}${sourceName}`
  const javaRelative = `api/src/main/java/${packagePath}${sourceName}`
  const kotlinAbsolute = path.resolve(repoRoot, kotlinRelative)
  const javaAbsolute = path.resolve(repoRoot, javaRelative)

  if (fs.existsSync(kotlinAbsolute)) return normalizeCoveragePath(kotlinAbsolute, repoRoot)
  if (fs.existsSync(javaAbsolute)) return normalizeCoveragePath(javaAbsolute, repoRoot)
  return normalizeCoveragePath(kotlinAbsolute, repoRoot)
}

function getOrCreateFileCoverageEntry(coverageByFile, filePath) {
  if (!coverageByFile.has(filePath)) {
    coverageByFile.set(filePath, {
      lineHits: new Map(),
      branchByLine: new Map(),
      methods: [],
      methodSeen: new Set(),
    })
  }
  return coverageByFile.get(filePath)
}

function collectBackendCoverageFromJacoco(jacocoPath, coverageByFile, repoRoot) {
  const xml = fs.readFileSync(jacocoPath, "utf8")
  const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "",
    isArray: (name) =>
      ["package", "sourcefile", "class", "method", "counter", "line"].includes(name),
  })
  const parsed = parser.parse(xml)
  const packages = (parsed.report ?? parsed).package ?? []

  for (const pkg of packages) {
    const packageName = pkg.name ?? ""
    if (!packageName.startsWith(BACKEND_PACKAGE_PREFIX)) continue

    // Line hits and branch data from <sourcefile> elements
    for (const sourceFile of (pkg.sourcefile ?? [])) {
      const filePath = resolveBackendSourcePath(packageName, sourceFile.name ?? "", repoRoot)
      const entry = getOrCreateFileCoverageEntry(coverageByFile, filePath)

      for (const line of (sourceFile.line ?? [])) {
        const lineNumber = Number.parseInt(line.nr, 10)
        if (Number.isNaN(lineNumber)) continue

        const ci = Number.parseInt(line.ci, 10)
        const hits = ci > 0 ? 1 : 0
        const existing = entry.lineHits.get(lineNumber) ?? 0
        entry.lineHits.set(lineNumber, Math.max(existing, hits))

        const mb = Number.parseInt(line.mb, 10) || 0
        const cb = Number.parseInt(line.cb, 10) || 0
        if (mb + cb > 0) {
          const prev = entry.branchByLine.get(lineNumber)
          if (!prev) {
            entry.branchByLine.set(lineNumber, {covered: cb, missed: mb})
          } else {
            entry.branchByLine.set(lineNumber, {
              covered: Math.max(prev.covered, cb),
              missed: Math.max(prev.missed, mb),
            })
          }
        }
      }
    }

    // Method data from <class>/<method> elements
    for (const cls of (pkg.class ?? [])) {
      const sourceFileName = cls.sourcefilename ?? ""
      if (!sourceFileName) continue
      const filePath = resolveBackendSourcePath(packageName, sourceFileName, repoRoot)
      const entry = getOrCreateFileCoverageEntry(coverageByFile, filePath)

      for (const method of (cls.method ?? [])) {
        const lineNumber = Number.parseInt(method.line, 10)
        if (Number.isNaN(lineNumber)) continue
        const methodCounter = (method.counter ?? []).find((c) => c.type === "METHOD")
        const isCovered = methodCounter
          ? Number.parseInt(methodCounter.covered, 10) > 0
          : false
        const methodKey = `${method.name ?? "?"}@${lineNumber}`
        if (!entry.methodSeen.has(methodKey)) {
          entry.methodSeen.add(methodKey)
          entry.methods.push({name: method.name ?? "?", line: lineNumber, hits: isCovered ? 1 : 0})
        }
      }
    }
  }
}

function createIstanbulFileCoverage(filePath, entry) {
  const statementMap = {}
  const statements = {}
  const branchMap = {}
  const branchHits = {}
  const fnMap = {}
  const fnHits = {}

  let stmtId = 0
  for (const [lineNumber, hits] of Array.from(entry.lineHits.entries()).sort((a, b) => a[0] - b[0])) {
    const id = String(stmtId++)
    statementMap[id] = {
      start: {line: lineNumber, column: 0},
      end: {line: lineNumber, column: 0},
    }
    statements[id] = hits
  }

  let branchId = 0
  for (const [lineNumber, {covered, missed}] of entry.branchByLine.entries()) {
    const id = String(branchId++)
    const total = covered + missed
    branchMap[id] = {
      loc: {start: {line: lineNumber, column: 0}, end: {line: lineNumber, column: 0}},
      type: "branch",
      locations: Array.from({length: Math.max(2, total)}, () => ({
        start: {line: lineNumber, column: 0},
        end: {line: lineNumber, column: 0},
      })),
    }
    // Approximate: first `covered` arms hit, remaining `missed` not hit
    branchHits[id] = [...Array(covered).fill(1), ...Array(missed).fill(0)]
  }

  let fnId = 0
  for (const {name, line, hits} of entry.methods) {
    const id = String(fnId++)
    fnMap[id] = {
      name,
      decl: {start: {line, column: 0}, end: {line, column: 0}},
      loc: {start: {line, column: 0}, end: {line, column: 0}},
    }
    fnHits[id] = hits
  }

  return {
    path: filePath,
    statementMap,
    s: statements,
    fnMap,
    f: fnHits,
    branchMap,
    b: branchHits,
  }
}

function normalizeFrontendFilePath(filePath, repoRoot) {
  // coverage-final.json paths are already normalized to frontend/... by
  // convert-frontend-coverage.mjs. Only handle: repo-relative and host-absolute.
  const normalized = normalizePathToPosix(filePath)
  if (path.isAbsolute(filePath)) {
    const repoRelative = toRepoRelativeFromAbsolute(filePath, repoRoot)
    if (repoRelative) return repoRelative
  }
  if (normalized.startsWith("frontend/") || normalized.startsWith("./frontend/")) {
    return stripLeadingDotSlash(normalized)
  }
  return normalizeCoveragePath(normalized, repoRoot)
}

function loadFrontendCoverage(frontendJsonPath, repoRoot) {
  const raw = JSON.parse(fs.readFileSync(frontendJsonPath, "utf8"))
  const inputMap = createCoverageMap(raw)
  const normalizedMap = createCoverageMap({})

  for (const filePath of inputMap.files()) {
    const fileCoverage = inputMap.fileCoverageFor(filePath).toJSON()
    fileCoverage.path = normalizeFrontendFilePath(fileCoverage.path ?? filePath, repoRoot)
    normalizedMap.addFileCoverage(fileCoverage)
  }

  return normalizedMap
}

function filterMergedCoverage(coverageMap, backendPrefix, repoRoot) {
  const frontendPrefix = "frontend"
  const normalizedBackendPrefix = normalizeCoveragePath(backendPrefix, repoRoot).replace(/\/+$/, "")
  const filtered = createCoverageMap({})

  for (const filePath of coverageMap.files()) {
    const normalizedPath = normalizeCoveragePath(filePath, repoRoot)
    const isFrontend = normalizedPath === frontendPrefix || normalizedPath.startsWith(`${frontendPrefix}/`)
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

function resolveHtmlSource(filePath, repoRoot) {
  const normalizedInput = normalizePathToPosix(filePath)
  const normalizedCoveragePath = normalizeCoveragePath(filePath, repoRoot)
  const candidates = []

  if (path.isAbsolute(filePath)) {
    candidates.push(path.resolve(filePath))
  }
  candidates.push(path.resolve(repoRoot, normalizedCoveragePath))
  candidates.push(path.resolve(repoRoot, "frontend", normalizedInput))
  candidates.push(path.resolve(repoRoot, normalizedInput))

  const seen = new Set()
  for (const candidate of candidates) {
    const absoluteCandidate = path.resolve(candidate)
    if (seen.has(absoluteCandidate)) {
      continue
    }
    seen.add(absoluteCandidate)
    if (fs.existsSync(absoluteCandidate)) {
      return fs.readFileSync(absoluteCandidate, "utf8")
    }
  }

  throw new Error(`Unable to lookup source: ${filePath}`)
}

function writeReports(coverageMap, outDir, repoRoot) {
  fs.rmSync(outDir, {recursive: true, force: true})
  fs.mkdirSync(outDir, {recursive: true})

  const defaultContext = createContext({
    dir: outDir,
    coverageMap,
    defaultSummarizer: "nested",
  })

  const htmlContext = createContext({
    dir: outDir,
    coverageMap,
    defaultSummarizer: "nested",
    sourceFinder: (filePath) => resolveHtmlSource(filePath, repoRoot),
  })

  istanbulReports.create("lcovonly", {file: "lcov.info"}).execute(defaultContext)
  istanbulReports.create("cobertura", {file: "Cobertura.xml"}).execute(defaultContext)
  istanbulReports.create("json", {file: "coverage-final.json"}).execute(defaultContext)
  istanbulReports.create("json-summary", {file: "coverage-summary.json"}).execute(defaultContext)
  istanbulReports.create("html", {subdir: "html"}).execute(htmlContext)

  const htmlIndex = path.join(outDir, "html", "index.html")
  if (fs.existsSync(htmlIndex)) {
    fs.copyFileSync(htmlIndex, path.join(outDir, "index.html"))
  }
}

function main() {
  const {jacocoFiles, frontendJsonFiles, outDir} = parseArgs(process.argv)

  for (const jacocoFile of jacocoFiles) {
    if (!fs.existsSync(jacocoFile)) {
      throw new Error(`Missing JaCoCo report: ${jacocoFile}`)
    }
  }
  for (const frontendJson of frontendJsonFiles) {
    if (!fs.existsSync(frontendJson)) {
      throw new Error(`Missing frontend coverage JSON: ${frontendJson}`)
    }
  }

  const repoRoot = path.basename(process.cwd()) === "frontend"
    ? path.resolve(process.cwd(), "..")
    : process.cwd()

  const mergedCoverage = createCoverageMap({})
  for (const frontendJson of frontendJsonFiles) {
    mergedCoverage.merge(loadFrontendCoverage(frontendJson, repoRoot))
  }

  const coverageByFile = new Map()
  for (const jacocoFile of jacocoFiles) {
    collectBackendCoverageFromJacoco(jacocoFile, coverageByFile, repoRoot)
  }

  for (const [filePath, entry] of coverageByFile.entries()) {
    mergedCoverage.addFileCoverage(createIstanbulFileCoverage(filePath, entry))
  }

  const filteredCoverage = filterMergedCoverage(mergedCoverage, DEFAULT_BACKEND_PREFIX, repoRoot)
  if (filteredCoverage.files().length === 0) {
    throw new Error("Merged coverage is empty after filtering.")
  }

  writeReports(filteredCoverage, outDir, repoRoot)
  console.log(
    `Merged coverage generated (${filteredCoverage.files().length} files):\n` +
      `- LCOV: ${path.join(outDir, "lcov.info")}\n` +
      `- Cobertura: ${path.join(outDir, "Cobertura.xml")}\n` +
      `- HTML: ${path.join(outDir, "html")}`
  )
}

main()
