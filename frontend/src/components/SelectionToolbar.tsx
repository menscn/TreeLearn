import { useCopy } from "../hooks/useCopy";
import { menuPosition, TOOLBAR_SIZE, type SelectionSnapshot } from "../utils/selection";
import { Icon, type GlyphName } from "./Icon";

export { menuPosition, TOOLBAR_SIZE } from "../utils/selection";
export type { MenuPlacement, SelectionRect, SelectionSnapshot } from "../utils/selection";

interface SelectionToolbarProps {
  selection: SelectionSnapshot | null;
  onAskAi: (text: string) => void;
  onCopy: (text: string) => void;
  onSelectAll: (text: string) => void;
}

const CELLS = [
  { action: "ask_ai", icon: "sparkle", label: "chat_word_ask_ai_desc", enabled: true },
  { action: "fork", icon: "action_fork", label: "chat_word_fork_desc", enabled: false },
  { action: "highlight", icon: "action_highlight", label: "chat_word_highlight_desc", enabled: false },
  { action: "note", icon: "action_note", label: "chat_word_note_desc", enabled: false },
  { action: "copy", icon: "action_copy", label: "chat_word_copy_desc", enabled: true },
  { action: "select_all", icon: "action_select_all", label: "chat_word_select_all_desc", enabled: true }
] as const satisfies ReadonlyArray<{
  action: string;
  icon: GlyphName;
  label: string;
  enabled: boolean;
}>;

export function SelectionToolbar({ selection, onAskAi, onCopy, onSelectAll }: SelectionToolbarProps) {
  const t = useCopy();
  if (selection === null) return null;
  const pos = menuPosition(
    selection.rect,
    TOOLBAR_SIZE,
    { width: window.innerWidth, height: window.innerHeight }
  );
  const handlers: Record<string, ((text: string) => void) | undefined> = {
    ask_ai: onAskAi,
    copy: onCopy,
    select_all: onSelectAll
  };
  return (
    <div
      aria-label={t("chat_word_menu")}
      className="selection-toolbar"
      data-placement={pos.placement}
      onMouseDown={(event) => event.preventDefault()}
      role="toolbar"
      style={{ top: pos.top, left: pos.left, width: TOOLBAR_SIZE.width, height: TOOLBAR_SIZE.height }}
    >
      {CELLS.map((cell, index) => {
        const label = cell.enabled
          ? t(cell.label)
          : `${t(cell.label)}，${t("chat_action_unavailable")}`;
        return (
          <span className="selection-toolbar-cell" key={cell.action}>
            {index > 0 ? <span aria-hidden="true" className="selection-toolbar-sep" /> : null}
            <button
              aria-label={label}
              data-word-action={cell.action}
              disabled={!cell.enabled}
              onClick={() => handlers[cell.action]?.(selection.text)}
              title={label}
              type="button"
            >
              <Icon name={cell.icon} />
            </button>
          </span>
        );
      })}
    </div>
  );
}
