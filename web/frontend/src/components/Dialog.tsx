import { useEffect, useRef, type ReactNode } from 'react';
import { X } from './icons';
import { IconButton } from './IconButton';

interface DialogProps {
  open: boolean;
  /** Already-translated title. */
  title: string;
  /** Already-translated close affordance label. */
  closeLabel: string;
  onClose: () => void;
  children?: ReactNode;
}

/**
 * Overlay primitive built on the native <dialog> element: modality, focus
 * trapping and Esc handling come from the platform; the glass backdrop comes
 * from the design system (dialog.dialog / ::backdrop in global.css).
 */
export function Dialog({ open, title, closeLabel, onClose, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    }
    if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog ref={ref} className="dialog" onClose={onClose} onCancel={onClose}>
      <div className="dialog__header">
        <h2 className="dialog__title">{title}</h2>
        <IconButton icon={X} label={closeLabel} onClick={onClose} />
      </div>
      <div className="dialog__body">{children}</div>
    </dialog>
  );
}