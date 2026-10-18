# Cursor → Codex 历史迁移映射

历史参考；当前入口见 [AGENTS.md](../../AGENTS.md)，不要据此选择执行模式或写入旧任务目录。

## Source-to-target mapping: agents

| Cursor source | Codex target |
|---|---|
| `.cursor/agents/frontend-dev.md` | `.codex/agents/frontend-dev.toml` |
| `.cursor/agents/java-dev.md` | `.codex/agents/java-dev.toml` |
| `.cursor/agents/python-dev.md` | `.codex/agents/python-dev.toml` |
| `.cursor/agents/android-dev.md` | `.codex/agents/android-dev.toml` |
| `.cursor/agents/requirement-decomposer.md` | `.codex/agents/requirement-decomposer.toml` |
| `.cursor/agents/verifier.md` | `.codex/agents/verifier.toml` |
| `.cursor/agents/contract-guardian.md` | `.codex/agents/contract-guardian.toml` |
| `.cursor/agents/db-migration-reviewer.md` | `.codex/agents/db-migration-reviewer.toml` |
| `.cursor/agents/android-no-kotlin-guard.md` | `.codex/agents/android-no-kotlin-guard.toml` |

## Source-to-target mapping: skills

| Cursor source | Codex target |
|---|---|
| `.cursor/skills/harness-run/SKILL.md` | `.codex/skills/harness-run/SKILL.md` |
| `.cursor/skills/lint-all/SKILL.md` | `.codex/skills/lint-all/SKILL.md` |
| `.cursor/skills/secret-scan/SKILL.md` | `.codex/skills/secret-scan/SKILL.md` |
| `.cursor/skills/commit-cn/SKILL.md` | `.codex/skills/commit-cn/SKILL.md` |
| `.cursor/skills/context-handoff/SKILL.md` | `.codex/skills/context-handoff/SKILL.md` |
| `.cursor/skills/entropy-gc/SKILL.md` | `.codex/skills/entropy-gc/SKILL.md` |
| `.cursor/skills/pr-prep/SKILL.md` | `.codex/skills/pr-prep/SKILL.md` |
| `.cursor/skills/release-android/SKILL.md` | `.codex/skills/release-android/SKILL.md` |
| `.cursor/skills/acceptance-checklist/SKILL.md` | `.codex/skills/acceptance-checklist/SKILL.md` |
| `.cursor/skills/env-bootstrap/SKILL.md` | `.codex/skills/env-bootstrap/SKILL.md` |
| `.cursor/skills/quota-check/SKILL.md` | `.codex/skills/quota-check/SKILL.md` |
| `.cursor/skills/daily-sync/SKILL.md` | `.codex/skills/daily-sync/SKILL.md` |

## Non-goals for this migration

- No business-code edits.
- Do not delete `.cursor`.
- No new dependencies.
- No automatic commit.
- No complex sync script.
