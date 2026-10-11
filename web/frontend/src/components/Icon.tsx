import type { LucideIcon } from 'lucide-react';

interface IconProps {
  icon: LucideIcon;
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * The single stroke language for the Web client: the v1.1 prototype draws
 * 1.7-1.8 round-cap outlines and lucide defaults to 2, so every glyph goes
 * through this wrapper to keep exactly one stroke width.
 */
export function Icon({ icon: Glyph, size = 'md', className }: IconProps) {
  return (
    <Glyph
      aria-hidden="true"
      strokeWidth={1.8}
      size={size === 'sm' ? 16 : 20}
      className={className}
    />
  );
}