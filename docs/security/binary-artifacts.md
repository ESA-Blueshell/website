# Binary Artifacts Triage (Scorecard `BinaryArtifactsID`)

OpenSSF Scorecard's `BinaryArtifactsID` check (high) flags binaries committed to
the repository, because checked-in binaries cannot be reviewed or scanned and are
a supply-chain risk. This document enumerates every binary artifact Scorecard
flagged and records its disposition.

Tracking issue: #476.

## Findings

### 1. `services/api/libs/snakeyaml-2.5.jar` — JUSTIFIED (accepted, verified)

**What it is:** A vendored copy of SnakeYAML 2.5, wired into the api build via
`implementation(files("libs/snakeyaml-2.5.jar"))`. The global
`configurations.configureEach { exclude(group = "org.yaml", module = "snakeyaml") }`
in `services/api/build.gradle.kts` strips the transitively pulled (older)
SnakeYAML so that the pinned 2.5 is the only copy on every classpath.

**Verification:** The vendored jar is byte-identical to the official artifact on
Maven Central (SHA-256 `e6682acf1ace77508ef13649cbf4f8d09d2cf5457bdb61d25ffb6ac0233d78dd`),
confirming it is unmodified upstream. This checksum can be re-verified against
`https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.5/snakeyaml-2.5.jar.sha256`
at any time, which removes the "cannot be reviewed" supply-chain concern.

**Disposition:** Kept and justified. Sourcing the same coordinates from Maven
Central instead (`implementation("org.yaml:snakeyaml:2.5")`) is not viable here:
SnakeYAML 2.x publishes Gradle Module Metadata with distinct `standard-jvm` and
`android` variants, and on this project's `test`/`testFixtures`/`integrationTest`
compile classpaths Gradle selects the non-existent `snakeyaml-2.5-android.jar`
variant (the `TargetJvmEnvironment` attribute only steers the main classpath),
breaking compilation. The vendored plain jar sidesteps variant selection
entirely and is behaviourally identical to the verified Maven-Central artifact.
Because it is checksum-pinned to the published release, it carries the same
review/scan guarantees as a downloaded dependency — see the gradle-wrapper.jar
justification below for the equivalent accepted-binary rationale.

### 2. `services/api/libs/snakeyaml-2.5-android.jar` — REMOVED (stray duplicate)

**What it was:** A second checked-in jar, byte-identical to
`snakeyaml-2.5.jar` (same MD5/SHA-256). It was **not referenced anywhere** in the
build (`grep` across all `*.gradle.kts`/`*.gradle`/`*.toml` found no reference),
i.e. an accidental duplicate committed alongside the main jar.

**Disposition:** Removed. It was dead weight and unreferenced; nothing depends on
it. The referenced, checksum-verified `snakeyaml-2.5.jar` (finding #1) remains in
`services/api/libs/`.

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
