import type { MouseEventHandler, Ref } from "react";
import { Icon, type GlyphName } from "./Icon";

interface IconButtonProps {
  glyph: GlyphName;
  label: string;
  action: string;
  id?: string;
  className?: string;
  disabled?: boolean;
  expanded?: boolean;
  controls?: string;
  buttonRef?: Ref<HTMLButtonElement>;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

/** 图标按钮的统一落点：44×44 热区、可访问名与悬停提示都跟按钮一起交付。 */
export function IconButton({
  glyph,
  label,
  action,
  id,
  className = "",
  disabled = false,
  expanded,
  controls,
  buttonRef,
  onClick
}: IconButtonProps) {
  return (
    <button
      ref={buttonRef}
      className={`icon-btn${className === "" ? "" : ` ${className}`}`}
      data-action={action}
      id={id}
      type="button"
      aria-label={label}
      title={label}
      aria-expanded={expanded}
      aria-controls={controls}
      disabled={disabled}
      onClick={onClick}
    >
      <Icon name={glyph} />
    </button>
  );
}
