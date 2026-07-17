# Binary Artifacts Triage (Scorecard `BinaryArtifactsID`)

OpenSSF Scorecard's `BinaryArtifactsID` check (high) flags binaries committed to
the repository, because checked-in binaries cannot be reviewed or scanned and are
a supply-chain risk. This document enumerates every binary artifact Scorecard
flagged and records its disposition.

Tracking issue: #476.

## Findings

### 1. `services/api/libs/snakeyaml-2.5.jar` — REMOVED

**What it was:** A vendored copy of SnakeYAML 2.5, wired into the api build via
`implementation(files("libs/snakeyaml-2.5.jar"))`. The global
`configurations.configureEach { exclude(group = "org.yaml", module = "snakeyaml") }`
in `services/api/build.gradle.kts` strips the transitively pulled (older)
SnakeYAML so that the pinned 2.5 could be re-added from the vendored file.

**Verification:** The vendored jar was byte-identical to the official artifact on
Maven Central (SHA-256 `e6682acf1ace77508ef13649cbf4f8d09d2cf5457bdb61d25ffb6ac0233d78dd`),
confirming it was unmodified upstream and safe to source as a normal dependency.

**Disposition:** Removed. Replaced with a declared dependency on the same
coordinates/version, `implementation("org.yaml:snakeyaml:2.5")`, resolved and
verified from Maven Central (already configured as the repository in
`settings.gradle.kts`). The version-pinning `exclude` is retained, so the
explicit 2.5 dependency is the only SnakeYAML on the classpath — behaviour is
unchanged.

### 2. `services/api/libs/snakeyaml-2.5-android.jar` — REMOVED (stray duplicate)

**What it was:** A second checked-in jar, byte-identical to
`snakeyaml-2.5.jar` (same MD5/SHA-256). It was **not referenced anywhere** in the
build (`grep` across all `*.gradle.kts`/`*.gradle`/`*.toml` found no reference),
i.e. an accidental duplicate committed alongside the main jar.

**Disposition:** Removed. It was dead weight and unreferenced; nothing depends on
it. With both jars gone, the now-empty `services/api/libs/` directory is removed
as well.

### 3. `gradle/wrapper/gradle-wrapper.jar` — JUSTIFIED (accepted, verified)

**What it is:** The Gradle wrapper bootstrap jar. It is part of the standard
Gradle wrapper contract: the `gradlew`/`gradlew.bat` scripts execute this jar to
download and launch the pinned Gradle distribution. It is expected to be checked
in and cannot be removed without breaking the wrapper for contributors and CI.

**Disposition:** Kept and justified as an accepted, verified artifact. To make the
distribution it fetches verifiable, `gradle/wrapper/gradle-wrapper.properties` now
carries a `distributionSha256Sum` for the pinned Gradle 9.5.1 distribution
(`bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f`), so the
wrapper verifies the downloaded distribution's checksum before use. This is the
recommended handling for the wrapper jar; the corresponding Scorecard alert should
be dismissed with this rationale.

## Summary

| Artifact | Disposition |
|----------|-------------|
| `services/api/libs/snakeyaml-2.5.jar` | Removed — sourced from Maven Central as `org.yaml:snakeyaml:2.5` |
| `services/api/libs/snakeyaml-2.5-android.jar` | Removed — unreferenced stray duplicate |
| `gradle/wrapper/gradle-wrapper.jar` | Kept & justified — required by Gradle wrapper contract; distribution now pinned with `distributionSha256Sum` |
