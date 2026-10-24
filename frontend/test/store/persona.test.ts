import { describe, expect, it } from "vitest";
import { hasCopy } from "../../src/copy";
import {
  PERSONA_ORDER,
  personaDemoKey,
  personaFromWireId,
  personaNameKey,
  personaSubKey,
  personaTagKey
} from "../../src/store/persona";
import { personaFor, resetWorkspace, setTopicPersona } from "../../src/store/workspace";

describe("人设档位与稳定 wireId", () => {
  it("七档顺序与规格一致，默认在最前", () => {
    expect([...PERSONA_ORDER]).toEqual([
      "default",
      "hand-holding",
      "strict-mentor",
      "study-buddy",
      "sarcastic-friend",
      "late-night",
      "learning-repair"
    ]);
  });

  it("已知 wire 值原样映射，不带回落信号", () => {
    expect(personaFromWireId("strict-mentor")).toEqual({ persona: "strict-mentor", fellBack: false });
    expect(personaFromWireId("default")).toEqual({ persona: "default", fellBack: false });
  });

  it("未知 wire 值回落默认档并带一次性回落信号", () => {
    expect(personaFromWireId("ghost-tier")).toEqual({ persona: "default", fellBack: true });
  });

  it("缺省（没有读到值）就是默认档，不算回落", () => {
    expect(personaFromWireId(null)).toEqual({ persona: "default", fellBack: false });
  });

  it("七档的档名、标签、定位与示例在文案表里都齐", () => {
    for (const persona of PERSONA_ORDER) {
      expect(hasCopy(personaNameKey(persona)), persona).toBe(true);
      expect(hasCopy(personaTagKey(persona)), persona).toBe(true);
      expect(hasCopy(personaSubKey(persona)), persona).toBe(true);
      expect(hasCopy(personaDemoKey(persona)), persona).toBe(true);
    }
  });
});

describe("主题级会话内人设状态", () => {
  it("未选过就是默认档，任何主题都不空", () => {
    resetWorkspace();
    expect(personaFor(0)).toBe("default");
    expect(personaFor(3)).toBe("default");
  });

  it("切换只落在被点的那个主题上", () => {
    resetWorkspace();
    setTopicPersona(1, "late-night");
    expect(personaFor(1)).toBe("late-night");
    expect(personaFor(0)).toBe("default");
    expect(personaFor(2)).toBe("default");
  });
});
