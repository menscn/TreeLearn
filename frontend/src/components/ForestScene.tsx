import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { copyFormat } from "../copy";
import type { ForestPlacement } from "../store/forestLayout";

/**
 * 斜俯瞰林地（T-070）：地面与树都是 SVG，坐标是「实测像素 == viewBox 单位」，
 * 所以 `ForestLayout` 的归一化数字乘上这个盒子就是画面上的位置，命中区也能按真实 px 保证 44。
 *
 * 地形常量、绘制顺序与树精灵配方逐值来自 Android `forest/ForestScene.java` 与
 * `forest/ForestTreeSprite.java`（两者又是原型 05 屏的移植），两端差异只在渲染介质。
 * 颜色一律走 CSS 类里的 token 变量，组件只给几何与不透明度。
 */

/** 量不到尺寸时（jsdom、首帧）用的兜底盒子；只影响这一帧的绘制，不影响判据。 */
const FALLBACK_BOX = { width: 1000, height: 620 };

/** 可点区域的最小边长，与 `--touch-min` 同值。 */
const HIT_MIN = 44;

/** 六条等高线的基准高度，占地面高度的比例。 */
const CONTOUR_BASES: readonly number[] = [0.858, 0.734, 0.609, 0.485, 0.360, 0.236];

/** 草簇与石头的落点（比例）与石头的半径（px）。 */
const GRASS_SPOTS: readonly (readonly [number, number])[] = [
  [0.144, 0.317],
  [0.846, 0.419],
  [0.246, 0.787],
  [0.585, 0.596],
  [0.913, 0.876]
];
const STONES: readonly (readonly [number, number, number, number])[] = [
  [0.800, 0.774, 7, 4],
  [0.174, 0.447, 6, 3.5],
  [0.528, 0.893, 5, 3]
];

export interface ForestSceneTree {
  index: number;
  title: string;
  placement: ForestPlacement;
}

interface ForestSceneProps {
  trees: readonly ForestSceneTree[];
  selected: number | null;
  onSelect: (index: number) => void;
}

function n(value: number): number {
  return Math.round(value * 100) / 100;
}

/** 原型 `mulberry32` 的移植：同 seed 同形状，不用平台随机数（重绘不能换树冠）。 */
function mulberry32(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6D2B79F5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function contourPath(base: number, width: number, height: number): string {
  const y = base * height;
  return [
    `M ${n(-0.10 * width)} ${n(y)}`,
    `C ${n(0.10 * width)} ${n(y - 0.06 * height)} ${n(0.26 * width)} ${n(y - 0.02 * height)} ${n(0.40 * width)} ${n(y - 0.06 * height)}`,
    `C ${n(0.56 * width)} ${n(y - 0.10 * height)} ${n(0.68 * width)} ${n(y - 0.06 * height)} ${n(0.85 * width)} ${n(y - 0.02 * height)}`,
    `C ${n(1.00 * width)} ${n(y + 0.01 * height)} ${n(1.08 * width)} ${n(y - 0.04 * height)} ${n(1.20 * width)} ${n(y - 0.02 * height)}`
  ].join(" ");
}

/** 闭合山丘：一个起点 + 三段三次贝塞尔，共 10 个点，全部是占宽/高的比例。 */
function blobPath(points: readonly (readonly [number, number])[], width: number, height: number): string {
  const at = (index: number): string => {
    const pair = points[index] as readonly [number, number];
    return `${n(pair[0] * width)} ${n(pair[1] * height)}`;
  };
  return `M ${at(0)} C ${at(1)} ${at(2)} ${at(3)} C ${at(4)} ${at(5)} ${at(6)} C ${at(7)} ${at(8)} ${at(9)} Z`;
}

const KNOLL: readonly (readonly [number, number])[] = [
  [0.733, 0.317],
  [0.641, 0.284],
  [0.641, 0.226],
  [0.723, 0.195],
  [0.810, 0.163],
  [0.928, 0.183],
  [0.949, 0.228],
  [0.969, 0.278],
  [0.846, 0.340],
  [0.733, 0.317]
];

const KNOLL_INNER: readonly (readonly [number, number])[] = [
  [0.759, 0.296],
  [0.697, 0.272],
  [0.703, 0.234],
  [0.759, 0.213],
  [0.821, 0.190],
  [0.897, 0.204],
  [0.910, 0.235],
  [0.923, 0.269],
  [0.836, 0.310],
  [0.759, 0.296]
];

function grassPath(x: number, y: number, width: number, height: number): string {
  const px = x * width;
  const py = y * height;
  return [
    `M ${n(px)} ${n(py)} Q ${n(px + 3)} ${n(py - 11)} ${n(px)} ${n(py - 17)}`,
    `M ${n(px + 5)} ${n(py)} Q ${n(px + 9)} ${n(py - 9)} ${n(px + 10)} ${n(py - 16)}`,
    `M ${n(px - 5)} ${n(py)} Q ${n(px - 8)} ${n(py - 9)} ${n(px - 12)} ${n(py - 14)}`
  ].join(" ");
}

/** 一棵斜俯瞰树的图元：树干落地、树冠在上、影子落在右下；配方与 Android 精灵逐项对齐。 */
function treeSprite(size: number, seed: number, tone: number): ReactNode[] {
  const shade = tone <= 0 ? 1 : tone;
  const random = mulberry32(seed === 0 ? 1 : seed);
  const cx = size * 0.50;
  const cy = size * 0.37;
  const baseY = size * 0.85;
  const crownTopY = size * 0.47;
  const lean = (random() * 2 - 1) * size * 0.05;
  const crownX = cx + lean;
  const tw = Math.max(1.7, size * 0.027);
  const parts: ReactNode[] = [
    <ellipse key="shadow-far" className="fs-shadow" cx={n(size * 0.60)} cy={n(size * 0.87)}
      rx={n(size * 0.32)} ry={n(size * 0.085)} opacity={0.10} />,
    <ellipse key="shadow-near" className="fs-shadow" cx={n(size * 0.54)} cy={n(size * 0.845)}
      rx={n(size * 0.17)} ry={n(size * 0.05)} opacity={0.07} />,
    <path
      key="trunk"
      className="fs-trunk"
      opacity={0.50}
      d={[
        `M ${n(cx - tw)} ${n(baseY)}`,
        `C ${n(cx - tw * 0.8)} ${n(size * 0.74)} ${n(crownX - tw * 0.45)} ${n(size * 0.60)} ${n(crownX - tw * 0.32)} ${n(crownTopY)}`,
        `L ${n(crownX + tw * 0.32)} ${n(crownTopY)}`,
        `C ${n(crownX + tw * 0.45)} ${n(size * 0.60)} ${n(cx + tw * 0.8)} ${n(size * 0.74)} ${n(cx + tw)} ${n(baseY)}`,
        "Z"
      ].join(" ")}
    />,
    <path key="branch-near" className="fs-branch" opacity={0.42} strokeWidth={n(tw * 0.65)}
      d={`M ${n(crownX - tw * 0.15)} ${n(size * 0.68)} Q ${n(crownX - size * 0.09)} ${n(size * 0.62)} ${n(crownX - size * 0.16)} ${n(size * 0.57)}`} />,
    <path key="branch-far" className="fs-branch" opacity={0.36} strokeWidth={n(tw * 0.6)}
      d={`M ${n(crownX + tw * 0.05)} ${n(size * 0.66)} Q ${n(crownX + size * 0.08)} ${n(size * 0.61)} ${n(crownX + size * 0.15)} ${n(size * 0.56)}`} />,
    <ellipse key="under-canopy" className="fs-under" cx={n(crownX + size * 0.01)} cy={n(cy + size * 0.075)}
      rx={n(size * 0.29)} ry={n(size * 0.155)} opacity={n(Math.min(0.24, 0.12 * shade))} />
  ];

  const lobes = 8 + Math.floor(random() * 4);
  for (let i = 0; i < lobes; i += 1) {
    const angle = (i / lobes) * Math.PI * 2 + (random() - 0.5) * 0.34;
    const outer = i < lobes - 3;
    const rx = size * (outer ? 0.13 + random() * 0.045 : 0.10 + random() * 0.035);
    const ry = size * (outer ? 0.095 + random() * 0.035 : 0.075 + random() * 0.03);
    const x = crownX + Math.cos(angle) * size * (outer ? 0.17 + random() * 0.035 : 0.06 + random() * 0.05);
    const y = cy + Math.sin(angle) * size * (outer ? 0.10 + random() * 0.03 : 0.04 + random() * 0.04);
    parts.push(
      <ellipse key={`lobe-${i}`} className="fs-canopy" cx={n(x)} cy={n(y)} rx={n(rx)} ry={n(ry)}
        opacity={n(Math.min(0.9, (0.54 + random() * 0.22) * shade))} />
    );
  }

  for (let i = 0; i < 9; i += 1) {
    const angle = random() * Math.PI * 2;
    const x = crownX + Math.cos(angle) * size * (0.27 + random() * 0.06);
    const y = cy + Math.sin(angle) * size * (0.16 + random() * 0.04);
    parts.push(
      <circle key={`dot-${i}`} className="fs-dot" cx={n(x)} cy={n(y)} r={n(size * 0.018)} opacity={0.30} />
    );
  }

  parts.push(
    <ellipse key="glow-1" className="fs-glow" cx={n(crownX - size * 0.10)} cy={n(cy - size * 0.09)}
      rx={n(size * 0.13)} ry={n(size * 0.08)} opacity={0.30} />,
    <ellipse key="glow-2" className="fs-glow" cx={n(crownX - size * 0.17)} cy={n(cy + size * 0.01)}
      rx={n(size * 0.075)} ry={n(size * 0.05)} opacity={0.22} />,
    <ellipse key="glow-3" className="fs-glow" cx={n(crownX - size * 0.01)} cy={n(cy - size * 0.15)}
      rx={n(size * 0.075)} ry={n(size * 0.045)} opacity={0.20} />
  );
  return parts;
}

/** 地面与树；选中态的光晕、地面圈与标题胶囊也在这里，页面只负责状态与文案。 */
export function ForestScene({ trees, selected, onSelect }: ForestSceneProps) {
  const boxRef = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState(FALLBACK_BOX);

  useEffect(() => {
    const node = boxRef.current;
    if (node === null) return;
    const measure = (): void => {
      const width = node.clientWidth;
      const height = node.clientHeight;
      if (width <= 0 || height <= 0) return;
      setBox((previous) => (previous.width === width && previous.height === height
        ? previous
        : { width, height }));
    };
    measure();
    if (typeof ResizeObserver !== "function") return;
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const { width, height } = box;
  const ordered = useMemo(
    () => [...trees].sort((left, right) => (left.placement.y - right.placement.y)
      || left.title.localeCompare(right.title)),
    [trees]
  );
  const selectedTree = trees.find((tree) => tree.index === selected) ?? null;
  // 名牌默认挂在树的脚下；脚下若紧贴着另一棵树（纵向 0.22 地面高、横向 0.30 地面宽以内），
  // 就翻到树冠上方，免得牌子压到邻树、读成在标那棵。
  const tagAbove = selectedTree !== null && trees.some((other) => {
    if (other.index === selectedTree.index) return false;
    const dx = Math.abs(other.placement.x - selectedTree.placement.x);
    const dy = other.placement.y - selectedTree.placement.y;
    return dy > 0 && dy < 0.22 && dx < 0.30;
  });
  const tagX = selectedTree === null
    ? 0
    : Math.min(width * 0.86, Math.max(width * 0.14, selectedTree.placement.x * width));
  const tagY = selectedTree === null
    ? 0
    : tagAbove
      ? selectedTree.placement.y * height - (selectedTree.placement.size * height) / 2 - 8
      : selectedTree.placement.y * height + (selectedTree.placement.size * height) / 2 + 6;

  return (
    <div className="forest-scene" ref={boxRef}>
      <svg
        className="forest-ground"
        data-forest-scene
        role="group"
        aria-label={copyFormat("cd_forest_scene", trees.length)}
        viewBox={`0 0 ${n(width)} ${n(height)}`}
        width="100%"
        height="100%"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <radialGradient id="forest-halo">
            <stop className="fs-halo-in" offset="0.32" />
            <stop className="fs-halo-out" offset="1" />
          </radialGradient>
        </defs>
        <g aria-hidden="true">
          {CONTOUR_BASES.map((base) => (
            <path key={`contour-${base}`} className="fs-contour" d={contourPath(base, width, height)} />
          ))}
          <path className="fs-contour" d={blobPath(KNOLL, width, height)} />
          <path className="fs-contour fs-contour-faint" d={blobPath(KNOLL_INNER, width, height)} />
          {GRASS_SPOTS.map(([x, y]) => (
            <path key={`grass-${x}-${y}`} className="fs-grass" d={grassPath(x as number, y as number, width, height)} />
          ))}
          {STONES.map(([x, y, rx, ry]) => (
            <ellipse key={`stone-${x}-${y}`} className="fs-stone"
              cx={n((x as number) * width)} cy={n((y as number) * height)} rx={rx as number} ry={ry as number} />
          ))}
        </g>
        {ordered.map((tree) => {
          const size = tree.placement.size * height;
          const cx = tree.placement.x * width;
          const cy = tree.placement.y * height;
          const isSelected = tree.index === selected;
          return (
            <g
              key={tree.index}
              className={`forest-tree${isSelected ? " selected" : ""}`}
              data-forest-tree={tree.index}
              data-selected={isSelected ? "true" : "false"}
              role="button"
              tabIndex={0}
              aria-label={tree.title}
              aria-current={isSelected ? "true" : undefined}
              onClick={() => onSelect(tree.index)}
              onKeyDown={(event) => {
                if (event.key !== "Enter" && event.key !== " ") return;
                event.preventDefault();
                onSelect(tree.index);
              }}
            >
              {isSelected ? (
                <g aria-hidden="true">
                  <circle className="fs-halo" cx={n(cx)} cy={n(cy)} r={n(size * 0.70)} />
                  <ellipse className="fs-ring" cx={n(cx)} cy={n(cy + size * 0.37)}
                    rx={n(size * 0.38)} ry={n(size * 0.08)} strokeWidth={Math.max(1, height / 620)} />
                </g>
              ) : null}
              <g aria-hidden="true" transform={`translate(${n(cx - size / 2)},${n(cy - size / 2)})`}>
                {treeSprite(size, tree.placement.seed, tree.placement.tone)}
              </g>
              <circle className="fs-hit" cx={n(cx)} cy={n(cy)} r={n(Math.max(size / 2, HIT_MIN / 2))} />
            </g>
          );
        })}
      </svg>
      <div className="forest-tag-layer" aria-hidden="true">
        {selectedTree === null ? null : (
          <span
            className={`forest-tag${tagAbove ? " above" : ""}`}
            data-forest-tag
            style={{ left: n(tagX), top: n(Math.min(height - 8, Math.max(8, tagY))) }}
          >
            {selectedTree.title}
          </span>
        )}
      </div>
    </div>
  );
}
