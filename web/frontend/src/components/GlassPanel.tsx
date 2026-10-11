import type { HTMLAttributes, ReactNode } from 'react';

interface GlassPanelProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'panel' | 'raised';
  children?: ReactNode;
}

/**
 * Translucent surface primitive: `panel` for persistent regions (sidebar,
 * header, side panels) and `raised` for overlays that float above content.
 * The reading column stays solid on purpose - long-form text keeps full
 * contrast and does not sit on a blurred backdrop.
 */
export function GlassPanel({ variant = 'panel', className, children, ...rest }: GlassPanelProps) {
  const classes = ['glass-panel', variant === 'raised' ? 'glass-panel--raised' : '', className]
    .filter(Boolean)
    .join(' ');
  return (
    <div className={classes} {...rest}>
      {children}
    </div>
  );
}