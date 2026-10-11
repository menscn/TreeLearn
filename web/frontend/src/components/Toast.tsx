interface ToastProps {
  /** Already-translated message; callers pass t('...'). */
  message: string;
  visible: boolean;
}

/**
 * Transient status primitive. The message is always supplied by a caller that
 * resolved it through i18n, so this component never carries user copy itself.
 */
export function Toast({ message, visible }: ToastProps) {
  if (!visible) {
    return null;
  }
  return (
    <div className="toast" role="status" aria-live="polite">
      {message}
    </div>
  );
}