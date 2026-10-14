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

  it('renders the label as visible text next to the icon when showLabel is set (D1)', () => {
    render(<IconButton icon={Languages} label="开始学习" variant="solid" showLabel />);
    const button = screen.getByRole('button', { name: '开始学习' });
    expect(button).toHaveClass('icon-button--with-label');
    expect(button).toHaveTextContent('开始学习');
    expect(button).toHaveAttribute('title', '开始学习');
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

// 纸上书房（方向 B，2026-10-12 用户拍板）的设计契约写死在 CSS 里：D4 去毛玻璃去光斑、
// D2 发丝线分隔、D3 衬线只做 display、D1 文字按钮。断言直接读取 global.css 与生成的
// tokens.css（vitest cwd = web/frontend），因为 jsdom 不计算样式，类名本身不足以证明
// 视觉契约真的落地。
describe('android baseline surfaces', () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'global.css'), 'utf8');
  const tokens = readFileSync(join(process.cwd(), 'src', 'styles', 'tokens.css'), 'utf8');

  it('drops backdrop blur everywhere (D4: depth comes from the paper ramp, not blur)', () => {
    expect(css).not.toMatch(/backdrop-filter/);
    expect(tokens).toMatch(/--glass-blur: 0px/);
  });

  it('keeps panel/overlay surfaces on paper tokens with hairline borders (D2/D4)', () => {
    expect(css).toMatch(/\.glass-panel \{[^}]*background: var\(--glass-panel\)[^}]*border: 1px solid var\(--hairline\)/);
    expect(css).toMatch(/dialog\.dialog \{[^}]*background: var\(--glass-raised\)/);
  });

  it('paints a flat paper body without background glows (D4)', () => {
    expect(css).toMatch(/body \{[\s\S]*?background-color: var\(--bg\)/);
    expect(css).not.toMatch(/radial-gradient/);
    expect(tokens).not.toMatch(/--bg-glow/);
    expect(tokens).not.toMatch(/--bg-wash/);
  });

  // 2026-10-13 用户裁决：《纸上书房》Web 独立色板作废（父需求 T-2026-10-13-123）。
  // 从此 tokens.css 里出现的每个色值都必须能在 Android 基础 token 文件里按名查到。
  it('derives every colour from the Android base token file (no local palette)', () => {
    const basePath = join(process.cwd(), '..', '..', 'Docs', '04-ux-design', 'design-system', 'tokens.json');
    const base = JSON.parse(readFileSync(basePath, 'utf8')) as {
      color: Record<string, { value: string }>;
    };

    // 字面量比对而非正则：hairline 的值是 rgba(...)，括号是正则元字符。
    expect(tokens).not.toMatch(/--web-[a-z]/);
    for (const [name, token] of Object.entries(base.color)) {
      expect(tokens).toContain(`--${name}: ${token.value};`);
    }
  });

  it('composites overlays only from Android surface and ink tokens', () => {
    expect(tokens).toMatch(/--glass-panel: rgba\(252, ?251, ?248,/);
    expect(tokens).toMatch(/--glass-scrim: rgba\(22, ?36, ?28,/);
    expect(tokens).toMatch(/--glass-border: var\(--line\)/);
  });

  it('keeps global.css free of literal colour values', () => {
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(css).not.toMatch(/\brgba?\(/);
  });
});

// D1 文字按钮 + D3 衬线 display 的 CSS 契约。
describe('paper typography and text buttons', () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'global.css'), 'utf8');

  it('carries a visible label next to the icon (D1)', () => {
    expect(css).toMatch(/\.icon-button--with-label \{/);
    expect(css).toMatch(/\.icon-button__label \{/);
    // 绿色上的字用 Android 的近白surface（同 `view_answer_actions.xml:104-112` 计数角标的写法）。
    expect(css).toMatch(/\.btn \{[^}]*background: var\(--green\)[^}]*color: var\(--surface\)/);
  });

  it('uses serif for display surfaces only (D3)', () => {
    expect(css).toMatch(/\.home__title \{[^}]*font-family: var\(--font-serif\)/);
    expect(css).toMatch(/\.settings-page__title \{[^}]*font-family: var\(--font-serif\)/);
    expect(css).toMatch(/\.md-heading \{[^}]*font-family: var\(--font-serif\)/);
    expect(css).toMatch(/\.composer__input \{[^}]*font: inherit/);
  });
});

// T-125：表面形制回到 Android（卡片 drawable 的等价契约）。
// 依据：res/drawable/bg_topic_card.xml（surface + radius_md，无描边，elevation→sh-1）、
// bg_library_card.xml（surface + hairline stroke + radius_md）、bg_card.xml（surface + radius_lg）。
describe('android surface form', () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'global.css'), 'utf8');

  it('gives the home topic card the bg_topic_card form (surface, r-md, sh-1, no stroke)', () => {
    expect(css).toMatch(/\.home__topic-card \{[^}]*padding: var\(--s4\)[^}]*border: none[^}]*border-radius: var\(--r-md\)[^}]*background: var\(--surface\)[^}]*box-shadow: var\(--sh-1\)/);
  });

  it('gives library rows the bg_library_card form (surface, hairline stroke, r-md, sh-1)', () => {
    for (const cls of ['library__topic-card', 'library__hit']) {
      expect(css).toMatch(new RegExp(`\\.${cls} \\{[^}]*padding: var\\(--s3\\) var\\(--s4\\)[^}]*border: 1px solid var\\(--hairline\\)[^}]*border-radius: var\\(--r-md\\)[^}]*background: var\\(--surface\\)[^}]*box-shadow: var\\(--sh-1\\)`));
    }
  });

  // bg_card.xml 只有 solid surface + corners radius_lg：无描边；投影由布局的 elevation 承担。
  it('gives settings-style lists the bg_card container form (surface, r-lg, sh-1, no stroke)', () => {
    for (const cls of ['providers__list', 'model-roles__list', 'files-page__list']) {
      expect(css).toMatch(new RegExp(`\\.${cls} \\{[^}]*border: none[^}]*border-radius: var\\(--r-lg\\)[^}]*background: var\\(--surface\\)[^}]*box-shadow: var\\(--sh-1\\)`));
    }
  });

  it('reads Android gutters instead of any 4px horizontal padding', () => {
    expect(css).not.toMatch(/padding: var\(--s[0-9]\) var\(--s1\)/);
  });

  it('gives the library search field the bg_field form (bg fill, hairline stroke, r-sm)', () => {
    expect(css).toMatch(/\.library__search-input \{[^}]*padding: var\(--s2\) var\(--s3\)[^}]*border: 1px solid var\(--hairline\)[^}]*border-radius: var\(--r-sm\)[^}]*background: var\(--bg\)/);
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