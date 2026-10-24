import { useEffect, useRef, useState } from "react";
import { copyFormat } from "../copy";
import { useCopy } from "../hooks/useCopy";
import { PERSONA_ORDER, personaNameKey, type PersonaId } from "../store/persona";
import { personaFor, setTopicPersona } from "../store/workspace";
import { Icon } from "./Icon";

interface PersonaPickerProps {
  topic: number;
  onIntro: () => void;
  onSwitched?: (persona: PersonaId) => void;
}

/**
 * 输入行的人设 chip 与跟手弹窗：选择只写会话内主题级状态（`store/workspace`），
 * 弹窗外部点击与 Escape 关闭；介绍页入口在底行，弹窗本身不承载介绍内容。
 */
export function PersonaPicker({ topic, onIntro, onSwitched }: PersonaPickerProps) {
  const t = useCopy();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const persona = personaFor(topic);
  const name = t(personaNameKey(persona));
  const chipLabel = copyFormat("cd_persona_chip", name);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === "Escape") setOpen(false);
    };
    const onPointer = (event: MouseEvent): void => {
      const root = rootRef.current;
      if (root !== null && !root.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("click", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("click", onPointer);
    };
  }, [open]);

  return (
    <div className="persona-picker" ref={rootRef} data-persona-picker>
      <button
        type="button"
        className="chip persona-chip"
        data-node="chat_persona_chip"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={chipLabel}
        title={chipLabel}
        onClick={() => setOpen(!open)}
      >
        <Icon className="icon chip-effort" name="sparkle" />
        <span data-persona-chip-label>{name}</span>
        <Icon className="icon chip-chevron" name="chevron_down" />
      </button>
      {open ? (
        <div
          className="persona-popover"
          data-persona-popover
          role="listbox"
          aria-label={t("persona_picker_label")}
        >
          <p className="popover-label">{t("persona_picker_label")}</p>
          {PERSONA_ORDER.map((id) => {
            const selected = id === persona;
            return (
              <button
                key={id}
                type="button"
                role="option"
                aria-selected={selected}
                className={`popover-item persona-item${selected ? " on" : ""}`}
                data-persona-row={id}
                onClick={() => {
                  setOpen(false);
                  setTopicPersona(topic, id);
                  onSwitched?.(id);
                }}
              >
                <span className="pi-t" data-persona-row-label>{t(personaNameKey(id))}</span>
                {selected ? (
                  <span className="pi-check" data-persona-check>
                    <Icon name="check" />
                  </span>
                ) : null}
              </button>
            );
          })}
          <div className="popover-sep" aria-hidden="true" />
          <button
            type="button"
            className="popover-item persona-more"
            data-persona-intro
            onClick={() => {
              setOpen(false);
              onIntro();
            }}
          >
            <span>{t("persona_intro_more")}</span>
            <Icon name="chevron_right" />
          </button>
        </div>
      ) : null}
    </div>
  );
}
