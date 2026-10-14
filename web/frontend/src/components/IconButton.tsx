import type { ButtonHTMLAttributes } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Icon } from './Icon';

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'title'> {
  icon: LucideIcon;
  /** Already-translated accessible name; callers pass t('...'). */
  label: string;
  variant?: 'ghost' | 'solid';
  /**
   * 纸上书房方向 D1（2026-10-12）：把可访问名同时渲染为可见文字，图标降级为前缀。
   * 可访问名不变（仍是 aria-label），测试按 name 查询不受影响。
   */
  showLabel?: boolean;
}

/**
 * Icon-first button primitive. 纸上书房方向（D1）下主操作与回答动作行通过
 * showLabel 呈现「图标 + 文字」；纯装饰/紧凑场景（顶栏语言切换、chevron 翻月）
 * 仍可只用图标，可访问名由翻译后的 aria-label 承载，同时作为原生 tooltip。
 */
export function IconButton({
  icon,
  label,
  variant = 'ghost',
  type = 'button',
  showLabel = false,
  className,
  ...rest
}: IconButtonProps) {
  const classes = ['icon-button', variant === 'solid' ? 'icon-button--solid' : '', showLabel ? 'icon-button--with-label' : '', className]
    .filter(Boolean)
    .join(' ');
  return (
    <button type={type} className={classes} aria-label={label} title={label} {...rest}>
      <Icon icon={icon} />
      {showLabel ? <span className="icon-button__label">{label}</span> : null}
    </button>
  );
}