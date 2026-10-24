import type { ReactNode, RefObject } from "react";
import { useCopy } from "../hooks/useCopy";
import { Icon } from "./Icon";
import { IconButton } from "./IconButton";

interface ComposerProps {
  value: string;
  placeholder: string;
  model: string;
  inputRef: RefObject<HTMLTextAreaElement | null>;
  onChange: (value: string) => void;
  inputId?: string;
  label?: string;
  note?: string;
  dockClassName?: string;
  dockDataNode?: string;
  modelNode?: string;
  personaSlot?: ReactNode;
  statusSlot?: ReactNode;
}

/**
 * 输入区：草稿是真的，发送与附件还没接通。
 * 两个按钮都禁用并在可访问名里说明原因，避免把演示稿的发送行为搬进生产。
 * 宿主可以换输入框 id、可访问名与说明；动作本身保持同一套未接通语义。
 * `personaSlot` 挂在输入行的模型 chip 之后，`statusSlot` 挂在输入区上沿（浮层，不占正文高度）。
 */
export function Composer({
  value,
  placeholder,
  model,
  inputRef,
  onChange,
  inputId = "chat-composer-input",
  label,
  note,
  dockClassName = "chat-dock",
  dockDataNode = "chat_composer",
  modelNode = "chat_model_chip",
  personaSlot = null,
  statusSlot = null
}: ComposerProps) {
  const t = useCopy();
  const unavailable = t("chat_action_unavailable");
  return (
    <div className={dockClassName} data-node={dockDataNode}>
      {statusSlot}
      <form className="composer" onSubmit={(event) => event.preventDefault()}>
        <textarea
          id={inputId}
          ref={inputRef}
          aria-label={label ?? t("chat_composer_label")}
          placeholder={placeholder}
          rows={2}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
        <div className="composer-row">
          {model === "" ? null : (
            <span className="chip" data-node={modelNode}>
              <Icon className="icon chip-effort" name="think" />
              <span data-node="model_chip_label">{model}</span>
              <Icon className="icon chip-chevron" name="chevron_down" />
            </span>
          )}
          {personaSlot}
          <span className="composer-spacer" />
          <IconButton
            action="chat-attach"
            className="composer-attach"
            glyph="chat_attach"
            label={`${t("chat_attach")} · ${unavailable}`}
            disabled
          />
          <IconButton
            action="chat-send"
            className="composer-send"
            glyph="chat_send"
            label={`${t("chat_send")} · ${unavailable}`}
            disabled
          />
        </div>
        <p className="composer-help">{note ?? t("chat_composer_note")}</p>
      </form>
    </div>
  );
}
