import { useCopy } from "../hooks/useCopy";
import { copyFormat } from "../copy";
import type { DesktopAttachment as Attachment } from "../platform/desktop";

export function DesktopAttachment({ attachment, disabled, selecting, onSelect, onRemove }: {
  attachment: Attachment | undefined;
  disabled: boolean;
  selecting: boolean;
  onSelect: () => void;
  onRemove: () => void;
}) {
  const t = useCopy();
  return <div className="desktop-attachment">
    <div className="desktop-attachment-actions">
      <button type="button" className="settings-action" data-action="desktop-select-attachment" disabled={disabled} onClick={onSelect}>
        {t(attachment ? "desktop_attachment_replace" : "desktop_attachment_select")}
      </button>
      {attachment ? <button type="button" className="settings-action" data-action="desktop-remove-attachment" disabled={disabled} onClick={onRemove}>{t("desktop_attachment_remove")}</button> : null}
      {selecting ? <span role="status">{t("desktop_attachment_selecting")}</span> : null}
    </div>
    <p className="field-note">{t("desktop_attachment_types")}</p>
    {attachment ? <div className="desktop-attachment-selected">
      <strong>{attachment.name}</strong>
      <p className="field-note">{copyFormat("desktop_attachment_size", attachment.size)}</p>
      <p className="field-note">{t("desktop_attachment_note")}</p>
      <details><summary>{t("desktop_attachment_preview")}</summary><pre data-attachment-preview>{attachment.preview}</pre></details>
    </div> : null}
  </div>;
}
