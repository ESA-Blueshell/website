# ADR-015: The Api Image Is Built by Cloud Native Buildpacks

## Status
Accepted

## Context

Once the imaging service took image decoding out of the api (ADR-014), the api image needed
nothing but a JRE. Until then it was a thin Dockerfile on `eclipse-temurin` Alpine: a shell, a
package manager, `apk add libwebp-tools` and the AOT cache's training run (ADR-013).

What the image has to be was settled before this decision:

- **No shell.** A shell in the pod is what an attacker who gets code execution uses next, and
  nothing the api does at run time needs one. Three runbooks used one, and change with it.
- **Any patched Java 25 LTS.** The vendor does not matter; a patched JRE arriving without anyone
  editing a Dockerfile does.
- **Signed, with provenance and an SBOM**, as every other image is.
- **Patches arrive as reviewed Renovate pull requests and ship with the next release**, not by a
  base image moving under a fixed tag.
- **The AOT cache stays**, trained under the `training` profile.

The per-release download of about 250 MB compressed, against about 60 MB from a thin Dockerfile,
was accepted.

Alternatives considered:

- **A thin Dockerfile on `distroless/java`.** About ten lines, with BuildKit's provenance and SBOM
  as today. But the JRE would be whatever that image ships, bumped as a digest nobody reads, and
  the training run would need an exec-form `RUN` against a base with no shell to debug it in.
- **The Temurin Dockerfile as it was.** It has a shell, which contradicts the first requirement
  unless one is stripped out by hand.

## Decision

**The api image is built by `bootBuildImage` with Paketo's tiny Java builder, and the api has no
Dockerfile.**

- **The builder is pinned by tag and digest** in `services/api/build.gradle.kts`, and a Renovate
  regex manager bumps it. The run image is the one the builder names by version, so the one bump
  moves the JRE and the run image together. The tiny run image has no shell and no package
  manager, and the image runs as the buildpack's `cnb` user (1002:1001).
- **The AOT cache is the buildpack's**, with `BP_JVM_AOTCACHE_ENABLED`. Its training run takes
  `TRAINING_RUN_JAVA_TOOL_OPTIONS`: the `training` profile, `-Dspring.context.exit=onRefresh`,
  `MaxRAM=2g`, G1, and the throwaway keys the hardening guard asks for, drawn per build. The
  buildpack prints that line, keys included, which is harmless because they are never used again.
- **The flags every start shares with the training run are baked in.** `-XX:+UseG1GC` is
  appended to `JAVA_TOOL_OPTIONS` through the environment-variables buildpack, after whatever the
  pod sets, and `SPRING_PROFILES_ACTIVE` defaults to `prod`. Native memory tracking and the Spring
  Cloud Bindings library, both Paketo defaults, are off: nothing reads either.
- **The heap and direct memory are named in the Deployment and the migrate Job.** Paketo's memory
  calculator sizes metaspace, the code cache and thread stacks from the container's limit and
  adds an `-Xmx` of its own, which overrides a heap percentage. So each manifest sets
  `JAVA_TOOL_OPTIONS` to an explicit `-Xmx` and `-XX:MaxDirectMemorySize=64M`, where the
  calculator's default of 10 MB is too little for Lettuce, JDA and Tomcat. The calculator refuses
  to start a JVM whose total does not fit the limit, so a heap set too large fails at once.
- **Provenance and the SBOM are GitHub artifact attestations.** A buildpack build records neither
  the way BuildKit does, so the build workflow describes the pushed manifest list with syft,
  attests that SBOM and the build's provenance, and pushes both beside it in the registry. The
  same manifest list is signed with cosign, as before.
- **The build proves the image boots.** CI builds the image into the runner's daemon and boots it
  under `-XX:AOTMode=on` with the limits and `JAVA_TOOL_OPTIONS` read from the two manifests,
  before anything is pushed. A pull request builds it whenever the jar can change.
- **The buildpacks' build cache survives between CI runs.** It is bind-mounted from a directory
  the Actions cache saves, keyed on the builder's digest, so the JRE is not downloaded again. The
  launch cache, which holds the layers of the build that just ran, is not worth the gigabyte.
- **The imaging service keeps its Dockerfile.** It needs libwebp linked in, which no Paketo run
  image carries, and it is already a static binary on a shell-less base.

## Consequences

- No one can `kubectl exec` a shell into the api. Break-glass starts `java` through the buildpack
  launcher, with its profile on the command line. The restore rehearsal lists the live uploads,
  and bringup copies them in, through a short-lived pod that mounts the api's storage claim.
- The uploads still hold files that earlier images wrote as other users, so the pod's init
  container still opens the volume up on every start.
- A release downloads about 250 MB compressed. The AOT cache and the application are one layer
  that changes every release.
- The JRE is BellSoft Liberica, Paketo's default, rather than Temurin.
- Changing a pod's memory limit means changing its `-Xmx` with it; the smoke boot and the
  calculator both fail loudly if they no longer fit.

## Related

- ADR-013: The Api Boots From a JVM AOT Cache, amended by this one for where its flags live
- ADR-014: Uploaded Images Are Decoded in an Isolated Service, which made a JRE-only image possible
- `platform/docs/break-glass.md`, `platform/docs/bringup-v2.md` and `scripts/restore.sh`
