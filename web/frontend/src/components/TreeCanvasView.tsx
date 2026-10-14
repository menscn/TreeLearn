import { useEffect, useState } from 'react';
import type { TreeLayoutResponse } from '../api/types';
import { loadTreeLayout } from '../store/topicData';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';

// C 树图视图（任务 T-2026-10-10-046，契约 §6.11）：几何全部消费服务端响应，本组件
// 不推导任何布局——只把 branches 画成树干/叶/苞、把 labels 画成名牌、把 hits 画成
// 触达区并转发点击导航。viewBox 直接取服务端 bounds（已含土壤带），缩放平移交给
// 浏览器/外层容器，避免在客户端二次实现几何。

interface TreeCanvasViewProps {
  topicId: string;
  currentBranchId: string | null;
  onNavigate: (branchId: string) => void;
}

const PATH_COLOR = 'var(--tree-trunk, #7a5230)';
const PATH_CURRENT = 'var(--green, #2f7d4f)';
const LABEL_FILL = 'var(--tree-label-bg, rgba(255,255,255,0.92))';
const LABEL_STROKE = 'var(--tree-label-border, #c9b79c)';
const INK = 'var(--ink, #26221c)';

export function TreeCanvasView({ topicId, currentBranchId, onNavigate }: TreeCanvasViewProps) {
  const { t } = useI18n();
  const [layout, setLayout] = useState<TreeLayoutResponse | null>(null);
  const [error, setError] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let active = true;
    setLayout(null);
    setError(null);
    loadTreeLayout(topicId, currentBranchId)
      .then((value) => {
        if (active) setLayout(value);
      })
      .catch(() => {
        if (active) setError('workspace.tree.loadFailed');
      });
    return () => {
      active = false;
    };
  }, [topicId, currentBranchId]);

  if (error !== null) {
    return (
      <div className="workspace__tree">
        <p className="workspace__note" role="alert">
          {t(error)}
        </p>
      </div>
    );
  }
  if (layout === null) {
    return (
      <div className="workspace__tree">
        <p className="workspace__note">{t('workspace.tree.loading')}</p>
      </div>
    );
  }

  const { bounds, branches, labels, hits } = layout;
  const pad = 12;
  const titleById = new Map(labels.map((label) => [label.branchId, label]));
  const pad2 = (value: number) => value.toFixed(2);

  return (
    <div className="workspace__tree">
      {branches.length === 0 ? (
        <p className="workspace__note">{t('workspace.tree.empty')}</p>
      ) : (
        <svg
          className="workspace__tree-canvas"
          role="img"
          aria-label={t('workspace.overview')}
          viewBox={`${pad2(bounds.minX - pad)} ${pad2(bounds.minY - pad)} ${pad2(bounds.maxX - bounds.minX + pad * 2)} ${pad2(bounds.maxY - bounds.minY + pad * 2)}`}
        >
          {/* Draw order: limbs first, then label plates, then hit rects on top (click-only). */}
          {branches.map((branch) => {
            const stroke = branch.onCurrentPath ? PATH_CURRENT : PATH_COLOR;
            const width = Math.max(branch.baseWidth, 1.4);
            return (
              <g key={branch.branchId} data-branch-id={branch.branchId} data-kind={branch.kind}>
                <line
                  x1={pad2(branch.baseX)}
                  y1={pad2(branch.baseY)}
                  x2={pad2(branch.tipX)}
                  y2={pad2(branch.tipY)}
                  stroke={stroke}
                  strokeWidth={pad2(width)}
                  strokeLinecap="round"
                />
                {branch.kind === 'LEAF' ? (
                  <ellipse
                    cx={pad2(branch.tipX)}
                    cy={pad2(branch.tipY)}
                    rx={pad2(9)}
                    ry={pad2(15)}
                    transform={`rotate(${pad2((branch.direction * 180) / Math.PI)} ${pad2(branch.tipX)} ${pad2(branch.tipY)})`}
                    fill={stroke}
                    opacity={0.9}
                  />
                ) : null}
                {branch.kind === 'BUD' ? (
                  <g>
                    <circle cx={pad2(branch.tipX)} cy={pad2(branch.tipY)} r={pad2(11)} fill={stroke} />
                    <text
                      x={pad2(branch.tipX)}
                      y={pad2(branch.tipY + 4)}
                      textAnchor="middle"
                      fontSize={11}
                      fill={LABEL_FILL}
                    >
                      {branch.hiddenDescendants}
                    </text>
                  </g>
                ) : null}
              </g>
            );
          })}
          {labels.map((label) => {
            const owner = branches.find((branch) => branch.branchId === label.branchId);
            const unread = owner?.ownUnread ?? 0;
            return (
            <g key={`label-${label.branchId}`}>
              <line
                x1={pad2(label.leaderX)}
                y1={pad2(label.leaderY)}
                x2={pad2(label.x + label.width / 2)}
                y2={pad2(label.y + label.height)}
                stroke={LABEL_STROKE}
                strokeWidth={1}
              />
              <rect
                x={pad2(label.x)}
                y={pad2(label.y)}
                width={pad2(label.width)}
                height={pad2(label.height)}
                rx={6}
                fill={LABEL_FILL}
                stroke={LABEL_STROKE}
                strokeWidth={1}
              />
              <text
                x={pad2(label.x + label.width / 2)}
                y={pad2(label.y + (label.showSummary && label.summary !== '' ? 17 : label.height / 2 + 5))}
                textAnchor="middle"
                fontSize={12}
                fill={INK}
              >
                {label.title === '' ? t('workspace.tree.untitled') : label.title}
              </text>
              {label.showSummary && label.summary !== '' ? (
                <text
                  x={pad2(label.x + label.width / 2)}
                  y={pad2(label.y + label.height - 8)}
                  textAnchor="middle"
                  fontSize={10}
                  fill={INK}
                  opacity={0.72}
                >
                  {label.summary}
                </text>
              ) : null}
              {unread > 0 ? (
                <g data-testid={`unread-badge-${label.branchId}`}>
                  <circle
                    cx={pad2(label.x + label.width - 9)}
                    cy={pad2(label.y + 9)}
                    r={pad2(8)}
                    fill={PATH_CURRENT}
                  />
                  <text
                    x={pad2(label.x + label.width - 9)}
                    y={pad2(label.y + 12.5)}
                    textAnchor="middle"
                    fontSize={10}
                    fill={LABEL_FILL}
                  >
                    {unread}
                  </text>
                </g>
              ) : null}
            </g>
            );
          })}
          {hits.map((hit) => {
            const label = titleById.get(hit.branchId);
            const name = label !== undefined && label.title !== '' ? label.title : hit.branchId;
            return (
              <rect
                key={`hit-${hit.source}-${hit.branchId}`}
                x={pad2(hit.x)}
                y={pad2(hit.y)}
                width={pad2(hit.width)}
                height={pad2(hit.height)}
                fill="transparent"
                role="button"
                aria-label={name}
                style={{ cursor: 'pointer' }}
                onClick={() => onNavigate(hit.branchId)}
              />
            );
          })}
        </svg>
      )}
    </div>
  );
}
