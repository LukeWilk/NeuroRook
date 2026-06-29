# Repo Review Report

This report combines both parts of the repo scan:

1. concrete correctness / reliability findings that looked important
2. coding style / structure findings that make the repo feel more or less "sloppy"

The codebase does not read as chaotic. It does, however, show a mix of solid foundations and several places where shortcuts, duplication, and brittle boundaries are starting to accumulate.

## Executive Summary

The most important technical findings were not cosmetic:

- the backend pipeline appears to mix per-channel data into a single buffer
- the board connection lifecycle has some brittle native-session and threading behavior
- the shared backend API makes blocking discovery work look cheap, which encourages risky UI-thread usage
- CI and release configuration have some reproducibility and credential-gating weaknesses
- at least one test gives false confidence around multi-channel behavior

Separately, the repo has a few structural smells:

- repeated Gradle configuration
- split version ownership
- hard-coded UI assumptions
- duplicated layout branches
- shared APIs that imply more platform parity than currently exists
- mutable global test hooks in production-facing code

## Correctness / Reliability Findings

### High: Multi-channel frames appear to be merged into one processing buffer

Files involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/DataAcquisition.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/Buffer.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/DataPipeline.kt`

Why this matters:

`DataAcquisition` emits one `RawFrame` per channel, but `buffer()` uses one shared `ArrayDeque<Double>` and just keeps overwriting the last `channel` value. That means downstream filtering, PSD, and band-power calculations can be built from concatenated samples coming from different channels while still being labeled as a single channel.

Why it is serious:

- it is a data correctness problem, not just a style problem
- multi-channel support is part of the product model already
- downstream UI or consumers can believe they are seeing channel-specific analysis when they may not be

Suggested direction:

- keep one buffer per channel all the way through processing, or
- make the data model explicitly multi-channel and update the pipeline accordingly

### High: `BoardConnectionManager` has brittle native session lifecycle handling

File involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/BoardConnectionManager.kt`

Why this matters:

`connect()` stores `boardShim` before `prepare_session()` has fully succeeded. If session preparation fails, the catch block resets state but does not clearly guarantee that the partially created native session and shim reference are safely torn down. There is also cross-thread access to `boardShim` during stop/close paths without any clear synchronization strategy.

Why it is serious:

- can leak or retain a stale native board session
- can produce confusing retry failures
- can make the logical state (`connected = false`) disagree with the actual native object state
- native APIs are exactly the wrong place to rely on optimistic thread-safety assumptions

Suggested direction:

- only publish `boardShim` once the session is valid
- make cleanup explicit on all failure paths
- guard board lifecycle operations behind a consistent synchronization policy

### High: Shared API boundary encourages blocking work from UI coroutines

Files involved:

- `shared/src/commonMain/kotlin/io/github/lukewilk/shared/api/BackendApi.kt`
- `shared/src/jvmMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/api/HardwareBackendApi.kt`

Why this matters:

The API marks connect/stream actions as `suspend`, but board discovery and serial-port suggestion calls are synchronous. In the JVM UI, those synchronous calls are made from Compose effects. That makes potentially expensive device discovery look like a cheap in-memory read and increases the chance of UI stalls.

Why it is serious:

- Compose callers are nudged toward main-thread I/O
- the interface hides cost and threading expectations
- future callers are likely to repeat the same pattern

Suggested direction:

- make expensive discovery operations `suspend`, or
- separate "instant state access" from "I/O discovery" into clearly different APIs

### Medium: CI credential gating is fragile

Files involved:

- `settings.gradle.kts`
- `.github/workflows/kover.yml`

Why this matters:

The GitHub Packages repository is enabled whenever `GHUSER` and `GHTOKEN` are non-null. The CI workflow writes those values into `local.properties` unconditionally. Empty values can therefore still count as configured credentials, which is an easy way to get confusing package resolution failures rather than a clean "not configured" path.

Suggested direction:

- require non-blank values before enabling the repo
- avoid writing empty secrets into `local.properties`

### Medium: Release automation is not fully reproducible

Files involved:

- `.github/workflows/semantic-release.yml`
- `.gitignore`

Why this matters:

The release workflow runs `npm install`, but the repo ignores `package-lock.json`. That means the Node dependency graph for release tooling is allowed to drift over time.

Suggested direction:

- commit the npm lockfile if npm remains part of CI and release automation
- prefer deterministic installs in CI

### Medium: One backend API method advertises soft failure but can hard-throw

File involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/api/HardwareBackendApi.kt`

Why this matters:

`removeWave(waveIndex: Int): Boolean` implies a simple success/failure contract, but it uses `removeAt(waveIndex)` without guarding the index. Invalid input can therefore throw rather than return `false`.

Suggested direction:

- either validate the index and return `false`
- or make the API contract explicit that it throws on invalid input

### Medium: Start-stream guarding is not obviously safe under concurrent callers

File involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/api/HardwareBackendApi.kt`

Why this matters:

`streamingJob` is checked and assigned with no synchronization. In a typical UI this may not bite often, but structurally it is still a read-check-write race.

Suggested direction:

- serialize stream lifecycle calls
- or make the guard atomic

### Medium: A functional test likely gives false confidence on multi-channel behavior

File involved:

- `hardwareBackend/src/jvmTest/kotlin/io/github/lukewilk/hardware/api/BackendApiFunctionalTest.kt`

Why this matters:

The test tries to confirm data arrives from all enabled channels, but it collects `0 until filtered.size`, which are sample indices, not channel IDs. That means the test can pass even if actual channel separation is broken.

Suggested direction:

- assert on real channel identity or per-channel emissions
- align the test with the actual data model instead of inferred array length

### Low: Some exception handling patterns are brittle

Files involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/DataAcquisition.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/BoardConnectionManager.kt`

Why this matters:

There are a few places where behavior depends on stack-trace matching or broad catch-and-log patterns. Those are pragmatic, but they are fragile over time and can hide failure semantics from callers.

Suggested direction:

- replace stack-trace heuristics with explicit error types or explicit cancellation handling
- avoid outer catch blocks that swallow failures after inner logic rethrows or partially handles them

## Style / Structure Findings

### 1. Build logic is duplicated across modules

Files involved:

- `build.gradle.kts`
- `composeApp/build.gradle.kts`
- `shared/build.gradle.kts`
- `hardwareBackend/build.gradle.kts`
- `androidApp/build.gradle.kts`

What feels sloppy:

The same Kover coverage variant block is repeated across modules. That creates a drift risk and makes build logic harder to maintain than it needs to be.

Suggested cleanup:

- extract shared Gradle conventions
- keep per-module build files focused on module-specific configuration

### 2. Version ownership is split

Files involved:

- `build.gradle.kts`
- `gradle/libs.versions.toml`

What feels sloppy:

Some plugin versions are centralized, others are hard-coded inline. That makes upgrades harder to audit and creates more than one source of truth.

Suggested cleanup:

- centralize versions consistently
- use the version catalog wherever practical

### 3. UI routing depends on tab order instead of explicit identity

File involved:

- `shared/src/commonMain/kotlin/io/github/lukewilk/ui/MainScaffold.kt`

What feels sloppy:

The hardware screen is selected by checking whether `selectedTab == 0`. That is a brittle implicit contract between menu order and screen routing.

Suggested cleanup:

- define explicit screen IDs or a sealed navigation model
- let menu order be presentation data, not routing logic

### 4. Shared UI contains duplicated layout trees

File involved:

- `shared/src/jvmMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`

What feels sloppy:

The compact and wide branches repeat large chunks of UI assembly and callback wiring. That is a maintainability smell because future changes can land in one branch and not the other.

Suggested cleanup:

- extract shared content pieces once
- keep layout differences narrow and local

### 5. Shared API surface over-promises platform parity

Files involved:

- `shared/src/jvmMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`
- `shared/src/androidMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`
- `shared/src/iosMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`

What feels sloppy:

The shared API suggests a common `HardwareScreen`, but Android and iOS currently provide placeholder implementations. That may be intentional, but as architecture it creates a misleading sense of parity.

Suggested cleanup:

- document the platform gap explicitly
- or narrow the shared abstraction until all targets provide meaningful behavior

### 6. A few UI layout constants drift across components

Files involved:

- `shared/src/commonMain/kotlin/io/github/lukewilk/ui/MainScaffold.kt`
- `shared/src/commonMain/kotlin/io/github/lukewilk/ui/elements/navigation/MenuSidebar.kt`

What feels sloppy:

The sidebar width defaults are not fully aligned across call sites and component defaults. This is minor, but it is exactly the kind of duplicated magic number that grows into visual drift later.

Suggested cleanup:

- centralize layout constants
- avoid duplicating dimensions in both the component and its callers

### 7. The repo uses a few overly broad conventions

Files involved:

- `.gitignore`

What feels sloppy:

The blanket `.*` ignore with selective exceptions works, but it is coarse. New dotfiles are easy to forget to track, and the ignore strategy is harder to reason about than explicit rules.

Suggested cleanup:

- prefer explicit ignore entries over broad hidden-file catch-alls

### 8. Mutable global test hooks leak into normal module code

File involved:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/Buffer.kt`

What feels sloppy:

Top-level mutable hooks for tests are easy to add and hard to contain. They make behavior depend on ambient global state rather than normal object wiring.

Suggested cleanup:

- inject collaborators or testing seams
- reserve mutable globals for rare cases only

## What The Repo Does Well

- The module split is generally understandable.
- Naming is mostly clear and readable.
- There is meaningful test coverage in several areas.
- The project has a reasonable architectural shape for a growing Kotlin Multiplatform app.
- The code does not read as casually thrown together; most issues are from drift and inconsistent boundaries rather than from total disorder.

## What Currently Feels "Sloppy"

This is the short version of the repo smell profile:

- repeated configuration instead of shared conventions
- hidden coupling through indexes and list order
- duplicated UI branches
- inconsistent async and threading boundaries
- temporary platform gaps surfaced through seemingly complete shared APIs
- global mutable hooks used as shortcuts
- some CI and release practices that are more convenient than disciplined

## Recommended Fix Order

If the goal is to improve both correctness and maintainability, the most useful order is:

1. Fix the per-channel buffering / processing model.
2. Harden `BoardConnectionManager` lifecycle and synchronization.
3. Normalize `BackendApi` around blocking vs suspend operations.
4. Fix the misleading multi-channel functional test.
5. Centralize duplicated Gradle coverage configuration and version ownership.
6. Replace index-based navigation assumptions with explicit screen identities.
7. De-duplicate `HardwareScreen` layout branches.
8. Clean up CI credential gating and release reproducibility.
9. Contain or remove mutable global test hooks.

## Bottom Line

The repo is not "sloppy" in the sense of being random or unreadable. It is better described as a codebase with decent foundations that is accumulating a few dangerous correctness issues and several maintainability shortcuts at the same time.

The important earlier findings were real and should stay front and center. The style and structure review does not replace them; it sits next to them.

## Immediate Next Steps

The best next step is to convert this report into a small number of focused workstreams and execute them in order of risk reduction.

### Workstream 1: Fix multi-channel pipeline correctness

Priority: highest

Files to start with:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/Buffer.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/DataPipeline.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/pipeline/DataAcquisition.kt`
- `hardwareBackend/src/jvmTest/kotlin/io/github/lukewilk/hardware/api/BackendApiFunctionalTest.kt`

Goal:

- ensure channel data remains channel-specific from acquisition through buffering and downstream processing

Acceptance criteria:

- each channel is buffered independently, or the pipeline is explicitly redesigned around a multi-channel frame model
- downstream filtered / FFT / band-power outputs can be traced to the correct channel
- tests fail if channel data is mixed

Why first:

- this is the clearest case where the app may produce wrong results while appearing to work

### Workstream 2: Harden board session lifecycle and threading

Priority: very high

Files to start with:

- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/BoardConnectionManager.kt`
- related connection / retry tests in `hardwareBackend/src/jvmTest/kotlin/io/github/lukewilk/hardware/`

Goal:

- make board session creation, cleanup, retry, and stop/close behavior explicit and thread-safe enough for native resource handling

Acceptance criteria:

- `boardShim` is not published until the session is valid
- failed connect paths leave no stale session state behind
- stream stop / close / reconnect behavior has deterministic cleanup rules
- tests cover failed prepare, reconnect, and shutdown behavior

Why second:

- lifecycle bugs around native libraries tend to be painful, intermittent, and expensive to debug later

### Workstream 3: Fix test confidence gaps

Priority: high

Files to start with:

- `hardwareBackend/src/jvmTest/kotlin/io/github/lukewilk/hardware/api/BackendApiFunctionalTest.kt`
- any pipeline tests that currently infer channels from array size rather than identity

Goal:

- make tests prove the intended behavior instead of only exercising happy paths

Acceptance criteria:

- channel-oriented tests assert on actual channel identity
- tests covering pipeline behavior would fail if samples were merged across channels
- brittle reflection or compiler-name-dependent test patterns are reduced where practical

Why third:

- it is safer to refactor once the tests are trustworthy

### Workstream 4: Normalize blocking vs suspend API boundaries

Priority: medium-high

Files to start with:

- `shared/src/commonMain/kotlin/io/github/lukewilk/shared/api/BackendApi.kt`
- `shared/src/jvmMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`
- `hardwareBackend/src/jvmMain/kotlin/io/github/lukewilk/hardware/api/HardwareBackendApi.kt`

Goal:

- make the API clearly communicate which calls are cheap, which are blocking, and which are safe from UI code

Acceptance criteria:

- expensive discovery operations are `suspend` or moved behind an explicitly async/discovery-oriented API
- UI code does not perform device discovery as if it were a trivial synchronous read
- threading expectations are obvious from the interface

Why fourth:

- this reduces UI freeze risk and makes future code easier to write correctly

### Workstream 5: Clean up structural maintainability issues

Priority: medium

Files to start with:

- `build.gradle.kts`
- `composeApp/build.gradle.kts`
- `shared/build.gradle.kts`
- `hardwareBackend/build.gradle.kts`
- `androidApp/build.gradle.kts`
- `shared/src/commonMain/kotlin/io/github/lukewilk/ui/MainScaffold.kt`
- `shared/src/jvmMain/kotlin/io/github/lukewilk/ui/HardwareScreen.kt`
- `.gitignore`

Goal:

- reduce drift, hidden coupling, and duplicated structure

Acceptance criteria:

- duplicated Gradle/Kover setup is consolidated
- routing is not based on hard-coded list indexes
- compact and wide hardware layouts share more code
- repo config and ignore rules are easier to reason about

Why fifth:

- these are important, but they are best handled once the correctness risks are under control

## Suggested Ticket Breakdown

If you want to turn this into issues, this would be a clean breakdown:

1. Fix per-channel buffering and pipeline processing.
2. Add regression tests for multi-channel separation.
3. Harden `BoardConnectionManager` connect / retry / close lifecycle.
4. Make backend discovery APIs async-safe for UI usage.
5. Consolidate duplicated Gradle coverage configuration.
6. Replace index-based navigation with explicit screen identifiers.
7. De-duplicate `HardwareScreen` compact vs wide layout composition.
8. Remove or contain global mutable test hooks.
9. Tighten CI credential gating and release reproducibility.

## Recommended Starting Change Set

If starting implementation now, the best first change set is:

1. fix channel separation in the backend pipeline
2. update the related backend functional test so it asserts real channel behavior
3. run the targeted backend tests before touching wider architecture

That gives the highest value for the smallest focused slice of work.
