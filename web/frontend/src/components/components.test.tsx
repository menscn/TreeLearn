import { fireEvent, render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import { Dialog } from './Dialog';
import { GlassPanel } from './GlassPanel';
import { IconButton } from './IconButton';
import { Toast } from './Toast';
import { Languages } from './icons';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
describe('IconButton', () => {
  it('is an icon-only button whose accessible name is the translated label', () => {
    const onClick = vi.fn();
    render(<IconButton icon={Languages} label="切换到 English" onClick={onClick} />);

    const button = screen.getByRole('button', { name: '切换到 English' });
    expect(button).toHaveAttribute('title', '切换到 English');
    fireEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('exposes the solid variant for primary actions', () => {
    render(<IconButton icon={Languages} label="primary action" variant="solid" />);
    expect(screen.getByRole('button', { name: 'primary action' })).toHaveClass('icon-button--solid');
  });
});

describe('GlassPanel', () => {
  it('defaults to the panel surface and switches to the raised surface', () => {
    const { rerender } = render(<GlassPanel data-testid="surface">content</GlassPanel>);
    expect(screen.getByTestId('surface')).toHaveClass('glass-panel');
    expect(screen.getByTestId('surface')).not.toHaveClass('glass-panel--raised');

    rerender(
      <GlassPanel data-testid="surface" variant="raised">
        content
      </GlassPanel>,
    );
    expect(screen.getByTestId('surface')).toHaveClass('glass-panel--raised');
  });
});

// 毛玻璃是设计系统的行为契约：面板/浮层必须真正应用 backdrop-filter 模糊，且在浏览器
// 不支持时保留半透明填充并强化描边。断言直接读取 global.css（vitest cwd = web/frontend），
// 因为 jsdom 不计算样式，类名本身不足以证明模糊真的落地。
describe('glass surface design system', () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'global.css'), 'utf8');

  // 断言顺序契约而非仅存在性：压缩器按 browserslist targets 只保留最后一个同名（等价）声明，
  // 现代 Chrome/Edge 已移除 -webkit-backdrop-filter，只认标准属性。若把标准属性写在 -webkit- 之前，
  // 构建后标准属性会被剥离（历史上正是如此，导致毛玻璃失效）。故每个规则都必须「-webkit- 在前、标准在后」。
  it('declares the -webkit- prefix before the standard backdrop-filter in every glass rule', () => {
    expect(css).toMatch(
      /\.glass-panel \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur\)\)/,
    );
    expect(css).toMatch(
      /\.glass-panel--raised \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur-raised\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur-raised\)\)/,
    );
    expect(css).toMatch(
      /\.app-shell__sidebar \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur\)\)/,
    );
    expect(css).toMatch(
      /\.app-shell__header \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur\)\)/,
    );
    expect(css).toMatch(
      /\.toast \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur-raised\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur-raised\)\)/,
    );
    expect(css).toMatch(
      /dialog\.dialog \{[^}]*webkit-backdrop-filter: blur\(var\(--glass-blur-raised\)\);[^}]*backdrop-filter: blur\(var\(--glass-blur-raised\)\)/,
    );
  });

  it('degrades to a translucent fill with a reinforced outline when backdrop-filter is unsupported', () => {
    expect(css).toContain('@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)))');
    expect(css).toMatch(/@supports not \(\(backdrop-filter: blur\(1px\)\) or \(-webkit-backdrop-filter: blur\(1px\)\)\) \{[\s\S]*?border-color: var\(--line\)/);
  });
});

// 模糊纯色不产生任何视觉差异，所以毛玻璃要能被感知，页面背景背后必须有可被模糊的层次。
// 断言直接读取 global.css 与生成的 tokens.css（vitest cwd = web/frontend），证明光斑确实
// 存在、取自 token 变量，且没有回退成写死的色值。
describe('page background depth layers', () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'global.css'), 'utf8');
  const tokens = readFileSync(join(process.cwd(), 'src', 'styles', 'tokens.css'), 'utf8');

  it('paints low-opacity mist spots behind the glass surfaces from tokens only', () => {
    expect(css).toMatch(/body \{[\s\S]*?radial-gradient\([^;]*var\(--bg-glow-mint\)/);
    expect(css).toMatch(/body \{[\s\S]*?radial-gradient\([^;]*var\(--bg-glow-sage\)/);
    expect(css).toMatch(/body \{[\s\S]*?radial-gradient\([^;]*var\(--bg-wash\)/);
    expect(css).toMatch(/body \{[\s\S]*?background-attachment: fixed/);
  });

  it('declares the background layer tokens and a more translucent panel fill', () => {
    expect(tokens).toMatch(/--bg-wash: rgba\(247,249,252,0\.55\)/);
    expect(tokens).toMatch(/--bg-glow-mint: rgba\(169,216,194,0\.38\)/);
    expect(tokens).toMatch(/--bg-glow-sage: rgba\(103,141,106,0\.14\)/);
    expect(tokens).toMatch(/--glass-panel: rgba\(247,249,252,0\.62\)/);
  });

  it('keeps global.css free of literal colour values', () => {
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(css).not.toMatch(/\brgba?\(/);
  });
});

describe('Toast', () => {
  it('announces the message through role=status only while visible', () => {
    const { rerender } = render(<Toast message="已保存" visible />);
    expect(screen.getByRole('status')).toHaveTextContent('已保存');

    rerender(<Toast message="已保存" visible={false} />);
    expect(screen.queryByRole('status')).toBeNull();
  });
});

describe('Dialog', () => {
  it('opens with the translated title and closes through the icon button', () => {
    const onClose = vi.fn();
    render(
      <Dialog open title="重命名分支" closeLabel="关闭" onClose={onClose}>
        body
      </Dialog>,
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('重命名分支');

    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});