import type { CopyKey } from "../copy";

/**
 * 七个内置人设的稳定 wireId（`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 2026-10-23 定）。
 * Web 端不持久化人设：权威在 Android 的 `topics.persona`，这里只承载会话内选择与文案键映射。
 */
export type PersonaId =
  | "default"
  | "hand-holding"
  | "strict-mentor"
  | "study-buddy"
  | "sarcastic-friend"
  | "late-night"
  | "learning-repair";

export const PERSONA_ORDER: readonly PersonaId[] = [
  "default",
  "hand-holding",
  "strict-mentor",
  "study-buddy",
  "sarcastic-friend",
  "late-night",
  "learning-repair"
];

const KEY_STEMS: Record<PersonaId, string> = {
  default: "persona_default",
  "hand-holding": "persona_hand_holding",
  "strict-mentor": "persona_strict_mentor",
  "study-buddy": "persona_study_buddy",
  "sarcastic-friend": "persona_sarcastic_friend",
  "late-night": "persona_late_night",
  "learning-repair": "persona_learning_repair"
};

export interface PersonaResolution {
  persona: PersonaId;
  fellBack: boolean;
}

const WIRE_IDS: ReadonlySet<string> = new Set(PERSONA_ORDER);

/**
 * 读取边界的全映射：未知 wire 值回落默认档并带一次性信号（与 Android 064 同口径）；
 * 没读到值（缺省）就是默认档，不算回落。
 */
export function personaFromWireId(wire: string | null | undefined): PersonaResolution {
  if (wire === null || wire === undefined) return { persona: "default", fellBack: false };
  if (WIRE_IDS.has(wire)) return { persona: wire as PersonaId, fellBack: false };
  return { persona: "default", fellBack: true };
}

export function personaNameKey(persona: PersonaId): CopyKey {
  return KEY_STEMS[persona] as CopyKey;
}

export function personaTagKey(persona: PersonaId): CopyKey {
  return `${KEY_STEMS[persona]}_tag` as CopyKey;
}

export function personaSubKey(persona: PersonaId): CopyKey {
  return `${KEY_STEMS[persona]}_sub` as CopyKey;
}

export function personaDemoKey(persona: PersonaId): CopyKey {
  return `${KEY_STEMS[persona]}_demo` as CopyKey;
}
