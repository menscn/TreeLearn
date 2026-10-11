import type { ButtonHTMLAttributes } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Icon } from './Icon';

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'title'> {
  icon: LucideIcon;
  /** Already-translated accessible name; callers pass t('...'). */
  label: string;
  variant?: 'ghost' | 'solid';
}

/**
 * Icon-only button primitive (the product has no text buttons): the glyph is
 * the whole button, and accessibility is carried by the translated
 * aria-label, which also becomes the native tooltip.
 */
export function IconButton({
  icon,
  label,
  variant = 'ghost',
  type = 'button',
  className,
  ...rest
}: IconButtonProps) {
  const classes = ['icon-button', variant === 'solid' ? 'icon-button--solid' : '', className]
    .filter(Boolean)
    .join(' ');
  return (
    <button type={type} className={classes} aria-label={label} title={label} {...rest}>
      <Icon icon={icon} />
    </button>
  );
}