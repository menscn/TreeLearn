# TreeLearn

> **A local-first, branching AI learning companion — every follow-up question grows exactly where it belongs.**
>
> [简体中文](README.md) | English

[![Platform](https://img.shields.io/badge/platform-Android%20API%2028%2B-3DDC84?style=flat-square)](android/)
[![Language](https://img.shields.io/badge/language-Java%2017-orange?style=flat-square)](android/)
[![License](https://img.shields.io/badge/license-Proprietary%20/%20All%20Rights%20Reserved-red?style=flat-square)](LICENSE)

---

## Why this exists

Learning an unfamiliar field in a regular AI chat tool tends to go like this:

> You ask q1, read answer a1, and it raises q2. You follow q2, and keep following it. By the time you look back, a1 has scrolled far above — along with the reading position you left it at.

Linear conversations interleave follow-up questions from different answers by timestamp, so the main thread and side explorations interrupt each other and the context stops being specific.

TreeLearn turns the conversation into **a tree**. Start from a root question; from any *stable answer*, grow an independent branch with its own isolated context. Explore freely, then return to the exact spot you branched from. Every branch can fork again — the structure ends up reflecting how you actually thought.

It is **local-first**: everything lives in SQLite on your own device, and the DeepSeek API key is stored only through the Android Keystore. There is no cloud account, no sync, no remote telemetry. The planned `backend/` is a desktop service bound to **the loopback interface only**, serving the PC-facing web UI and holding keys on your behalf — it is not a remote server (scope and boundaries: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`).

> **Status: early-stage work in progress.** All five modules — `:core`, `:data`, `:ai`, `:tree-layout`, `:app` — contain source and build an installable APK (schema version: `DATABASE_VERSION` in `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`); screens and features are still iterating on user feedback. Do not expect a mature product.

---

## Core concepts

| Concept | Meaning |
| :--- | :--- |
| **Topic** | A complete conversation tree. Its display identity reuses the **root branch title** — no separate topic name is maintained, so a tree never has two names. |
| **Branch** | An independent conversation path on the tree. The root cannot be archived; non-root branches can be archived or deleted whole. |
| **Stable Answer** | A fully generated answer version. **Only stable answers can serve as a fork source** — partial answers are read-only. |
| **Context Snapshot** | The frozen context background captured when a branch is born. Later content from the parent branch **never** flows back and contaminates the child. |
| **Thinking Mode** | DeepSeek reasoning output is collapsed by default, persisted, and carried into subsequent context. |

---

## Functional scope

21 P0 requirements (F01–F21), all derived from a frozen requirements baseline. See [PRD V3.1](Docs/03-prd-draft/V3.1-%E7%9F%A5%E8%8A%BD%EF%BC%88TreeLearn%EF%BC%89%E4%BA%A7%E5%93%81%E9%9C%80%E6%B1%82%E6%96%87%E6%A1%A3%EF%BC%88PRD%EF%BC%89.md).

| Group | Features |
| :--- | :--- |
| **Forming the tree** | Multi-topic lifecycle & atomic first creation · Root title as topic identity · Independent conversation trees from stable answers · Layered context & creation snapshots |
| **Navigating back** | Focused reading & position restore · Layer-by-layer mobile navigation · Tree overview canvas (branch length grows with turns) · Titles, summaries & search · Unread state · Non-root subtree archive & delete |
| **AI & resilience** | Generation state & fork eligibility · Tree-aware recommended questions · On-device data & DeepSeek config · Recoverable AI/network failures · Graceful degradation of AI-assisted generation |
| **Added in V3.1** | Unified home information architecture · AI-recommended topics (scrolling marquee) · Model & thinking-mode selection · Text-selection actions & quoted follow-up · Answer-level actions & AI summary · Adaptive learning-tutor prompts |

---

## Architecture

```text
:app          ->  Activity / XML views / navigation / DI wiring
:data  :ai    ->  :core
:core         ->  Domain model (pure JVM, no android.* imports)
:tree-layout  ->  Tree geometry (pure JVM)
```

| Module | Responsibility | Explicitly forbidden |
| :--- | :--- | :--- |
| `:app` | Activities, XML views, navigation, screen controllers, generation coordinator | No SQL, no DeepSeek JSON assembly |
| `:core` | Domain entities, state machines, use cases, repository & gateway interfaces, vendor-neutral prompt contracts | No Android / SQLite / HTTP / Kotlin |
| `:data` | SQLite schema/DAOs/transactions/migrations, search, read state, Keystore keys | No business flow decisions, no DeepSeek calls |
| `:ai` | DeepSeek HTTPS/SSE, context encoding, versioned prompt registry | No View or database access |
| `:tree-layout` | Tree geometry, label collision, hit testing, viewport fitting | No database reads, no persisted pixel coordinates |

### Toolchain and constraints

| Item | Value |
| :--- | :--- |
| Gradle / AGP | 8.14.3 / 8.13.2 |
| JDK | 17 (source and bytecode level 17) |
| compileSdk / targetSdk | 36 |
| minSdk | 28 |
| Language | **Java-only** |
| Third-party deps (main source) | **Zero** |

**Java-only is a hard constraint, not a preference.** The app must not ship Kotlin bytecode, for APK size and runtime predictability reasons. This is enforced mechanically: static scanning + Gradle dependency reports + **a direct scan of the dex string table of built APKs**.

The dex-level stage exists because of a trap that was actually hit once: `androidx.test:monitor:1.6.1` declares **no** Kotlin dependencies in its POM — the dependency graph looks perfectly clean — yet two of its classes (`OutputDirCalculator` among them) are Kotlin-compiled, and the device throws `ClassNotFoundException: kotlin.jvm.internal.Lambda`. No amount of dependency-graph inspection catches it.

For the same reason, AndroidX is avoided in the main source set: `appcompat`, `core`, and the test artifacts all transitively pull in `kotlin-stdlib`. Main code uses platform APIs directly, yielding an `app-debug.apk` of roughly 70 KB across 3 dex files, with zero third-party dependencies and zero Kotlin bytecode.

The binding authority is the measured artifact, not the declared dependency set. See [`android/README.md`](android/README.md) for verification details.

---

## Getting started

```bash
# 1. Point JAVA_HOME at JDK 17 (the Gradle daemon accepts 17 or 21; compilation uses the Java 17 toolchain)
export JAVA_HOME=/path/to/jdk17

# 2. Locate your Android SDK
echo "sdk.dir=/path/to/Android/SDK" > android/local.properties

# 3. Build
cd android
./gradlew :app:assembleDebug

# 4. Run everything (including dependency-direction guards and dex-level Java-only checks)
./gradlew check
```

See [`android/README.md`](android/README.md) for common commands, release signing, and DeepSeek key configuration.

> **Release signing:** the keystore lives outside the repository. Credentials are read from process environment variables (`TREELEARN_KEYSTORE_PATH`, etc.), falling back to the gitignored `android/keystore.properties`. Keys and passphrases never enter the repository, logs, APK, or delivery reports.

---

## Repository layout

```text
Treelearn/
├─ android/               Android client (multi-module Gradle project)
│  ├─ app/                UI layer
│  ├─ core/               Domain layer (pure JVM)
│  ├─ data/               Local persistence
│  ├─ ai/                 DeepSeek gateway
│  ├─ tree-layout/        Tree layout math
│  └─ scripts/            Guard and dev scripts
├─ Docs/                  Requirements / PRD / UX / technical design
├─ artifacts/             Guard and acceptance evidence
└─ .trae/                 Harness rules, roles, and tasks
```

| Document | Contents |
| :--- | :--- |
| [`Docs/01-demand/`](Docs/01-demand) | Structured requirements baseline V3.0 / V3.1 increment |
| [`Docs/03-prd-draft/`](Docs/03-prd-draft) | Product requirements V3.1 |
| [`Docs/04-ux-design/`](Docs/04-ux-design) | High-fidelity UX prototype + [design-system tokens](Docs/04-ux-design/design-system) shared by Figma and Android |
| [`Docs/05-technical-design/`](Docs/05-technical-design) | Local-first architecture, prompt design, SQLite design |
| [`android/README.md`](android/README.md) | Build, guards, module contracts, common commands |

---

## Engineering process

This repository runs a **Harness** workflow (`AGENTS.md` + `.trae/`): requirements are clarified and frozen first, then decomposed into tasks with a <= 400-line budget. Enforcement requires TDD with "RED → GREEN" evidence trails, and an independent verifier accepts work against stack-specific guards.

- `.trae/rules/` — per-stack rules and baselines (Android / backend / SQL / contracts / shared state / observability)
- `.trae/tasks/` — task contracts, mechanically validated by `.trae/scripts/validate_tasks.py`
- `artifacts/evidence/` — execution evidence per guard run and acceptance pass (each file carries its own command line and exit code)
- `.agents/skills/` — repo-level skills discovered by Codex; currently `figma-build` and `figma-codegen` (migrated verbatim from figwright 0.5.0)
- Design workflow — Figma is reached through the figwright MCP server: one-time setup lives in the user-level `~/.codex/config.toml`, the Figma desktop app imports the plugin manifest; health probe `node .trae/scripts/dev/probe_figwright_mcp.mjs`, four-way design-token parity guard `python .trae/scripts/dev/check_design_tokens.py`, frontend CSS variables generated by `python .trae/scripts/dev/gen_design_tokens_css.py`. Conventions and troubleshooting: [`.trae/rules/ux-design.md`](.trae/rules/ux-design.md)

---

## License

> **This project is not open source. No open-source license has been granted.**

The code is copyrighted. Use, copying, modification, distribution, and commercial use require prior written permission. Public or private repository visibility does **not** constitute an implied license. Full terms are in [`LICENSE`](LICENSE).

Third-party dependencies remain governed by their own respective licenses; this notice does not cover those components.

---

Copyright (c) 2026 TreeLearn Project. All rights reserved.
