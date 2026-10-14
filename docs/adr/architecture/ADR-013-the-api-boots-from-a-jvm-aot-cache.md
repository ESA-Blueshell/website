# ADR-013: The Api Boots From a JVM AOT Cache

## Status
Accepted

## Context

A release boots the api three times in a row: the migrate Job, the canary and the primary on
promotion. Each start took about 85 seconds in production (`Started ApiApplicationKt in 80.532
seconds` on v1.23.0), on a pod limited to 1.5 CPU, so a release spent around four minutes on
cold starts alone.

Most of a Spring Boot start is loading and linking classes. Java 25 can do that once, ahead of
time: a training run records the classes an application loads and the profile of its early
methods into an AOT cache (JEP 483 and JEP 515), and a later start with the same JVM, flags and
classpath maps them in instead. Measured on the image, in Docker at 1.5 CPU and 2 GiB:

| Start | Boot |
|---|---|
| v1.23.0's flags: fat jar, ZGC, AlwaysPreTouch | 14.1 to 17.6s |
| Extracted jar, G1, no cache | 9.9 to 11.4s |
| Extracted jar, G1, AOT cache | 6.3 to 7.1s |
| AOT cache trained and run under ZGC | 9.3 to 10.0s, at twice the memory |

At 0.75 CPU the same start took 60.5 seconds without the cache and 17 to 19 with it, so the less
CPU the pod has, the more the cache saves.

Alternatives considered:

- **GraalVM native image.** It starts in about a second, but JDA, the Google client and the Vault
  config import all need reachability metadata, the build takes ten minutes and several gigabytes,
  and it fixes the profiles at build time, which breaks running the migrate Job from the
  production image with `prod,migrate`.
- **Spring AOT on the JVM.** It saves perhaps another 10 to 15%, for the same price: `@Profile`
  and `@ConditionalOnProperty` beans are decided at build time, which `DatabaseLoginRotation` and
  the migrate profile both depend on deciding at start.
- **Cloud Native Buildpacks.** Paketo trains the same cache with `BP_JVM_AOTCACHE_ENABLED`, but
  its run images have no `cwebp`, which the api checks for at start. It is the planned way to
  build the api image once image conversion leaves the api (#2449).
- **More CPU.** It helps every start, cached or not, and is weighed on its own (#2444).

## Decision

**The api image carries a JVM AOT cache trained while it is built, and every start of the api
loads it.**

- **The training run is one start of the application, as far as a refreshed context,** with
  `-Dspring.context.exit=onRefresh`: every singleton is created and nothing is started. It runs
  under the `training` profile, which turns Liquibase and Vault off, stops Hibernate reading
  database metadata and names the dialect, so it reaches no database, Vault or Valkey. The keys
  the hardening guard asks for are drawn at random for the run, so no profile can start the api
  on a key that ships in the image.
- **A bean that reaches a remote service while the context refreshes fails the image build.**
  The training run exits non-zero and the build stops, rather than writing a cache of a start that
  went wrong.
- **The JVM flags live in the image and nowhere else.** `JDK_JAVA_OPTIONS` carries them to the
  training run and to every start, and the Deployment and the migrate Job set no `command`. A JVM
  whose heap is laid out differently ignores the cache with one warning and boots cold: ZGC, or a
  heap past 32 GB, which turns compressed pointers off. So the training run's heap is sized for a
  2 GiB pod, not for whatever host builds the image.
- **G1, named.** The cache trained under ZGC saved less, ZGC with AlwaysPreTouch cost about three
  seconds and twice the memory at start, and a 2 GiB pod has no pause problem for ZGC to solve.
  It is named rather than left to the JVM, which picks Serial below 1792 MB, and the migrate Job
  is given 1536 MiB.
- **The build proves the cache loads.** Both the pull request image check and the main build boot
  the built image under `-XX:AOTMode=on`, which turns an ignored cache into a failure, in the
  memory and CPU of the api's pod and of the migrate Job, before anything tags it.
- **The cache is trained again on every build.** It belongs to one JDK build and one classpath,
  so a new jar or a Renovate bump of the base image retrains it without anyone asking.

## Consequences

- The image gains about 196 MB, 44 MB compressed, in a layer that changes with every release,
  and the build about 40 seconds for the training run.
- The `training` profile has to keep refreshing without remote services. A new bean that
  connects while it is created needs its connection deferred to start, or a training-profile
  setting, or the image build fails.
- The training run reaches neither Vault nor a database, so the classes behind the Vault config
  import, its leases and Liquibase load cold. The migrate Job, which is mostly those, gains less
  than the api does.
- The training run is a start, not a workload: JEP 515's method profiles cover only what runs
  while the context refreshes. A training run that served requests would warm more, at the cost
  of a running database during the build.
