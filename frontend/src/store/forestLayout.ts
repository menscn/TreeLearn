/**
 * 森林的地面几何（Web 侧，T-070）：与 Android `forest/ForestLayout.java` 同常量同算法的纯函数。
 *
 * 树的位置只由主题 id 决定，不看行序、不看最近学习，所以新主题不会把已有的树重排。
 * 归一化坐标交给场景去乘它自己拿到的像素，两端因此能在不同介质上画出同一片林地。
 */

/** 空地的归一化边界；原型的树就长在这些留白之间。 */
export const MIN_X = 0.10;
export const MAX_X = 0.90;
export const MIN_Y = 0.13;
export const MAX_Y = 0.80;

/** 原型最小/最大树精灵（788px 地面上的 54px 与 100px）换算成地面高度的比例。 */
export const MIN_SIZE = 0.0685;
export const MAX_SIZE = 0.1269;

/** 超过这个棵数才做分离；不超过时黄金角螺旋本身就是均匀的。 */
export const COMFORTABLE_TREES = 18;

/** 9 段枝就是原型最大的树（size 100），再多也不再长大。 */
const SIZE_CAP_SEGMENTS = 9;

const GOLDEN_ANGLE = 2.399963229728653;
const CENTER_X = 0.50;
const CENTER_Y = 0.465;
const RADIUS_X = 0.40;
const RADIUS_Y = 0.335;
const MIN_GAP = 0.048;
const SEPARATION_ROUNDS = 40;

export interface ForestPlacement {
  x: number;
  y: number;
  /** 精灵尺寸，占地面高度的比例。 */
  size: number;
  /** 原型的树冠深浅，0.80..1.15。 */
  tone: number;
  /** 与位置同源的非零精灵种子。 */
  seed: number;
}

/**
 * 按输入顺序给每个主题放一棵树。
 *
 * @param topicIds     主题的稳定 id，位置的唯一来源
 * @param branchCounts 每个主题的段枝数，只用来定精灵大小；读不到（null）按 1 段算
 */
export function forestLayout(topicIds: readonly string[], branchCounts: readonly (number | null)[]): ForestPlacement[] {
  const count = topicIds.length;
  if (branchCounts.length !== count) {
    throw new Error(`每个主题需要一个段枝数，收到 ${count} 个 id 和 ${branchCounts.length} 个计数`);  // i18n-ignore: 诊断信息，只在开发期抛到控制台，不渲染到 UI
  }
  const order = stableOrder(topicIds);
  const density = densityFor(count);
  const xs = new Float64Array(count);
  const ys = new Float64Array(count);
  const placed: ForestPlacement[] = new Array(count);

  for (let rank = 0; rank < count; rank += 1) {
    const index = order[rank] as number;
    const hash = hashOf(topicIds[index] as string);
    position(rank, count, hash, xs, ys);
    const branches = branchCounts[index] ?? 1;
    placed[index] = {
      x: xs[rank] as number,
      y: ys[rank] as number,
      size: sizeFor(branches, density),
      tone: toneFor(hash),
      seed: seedFor(hash)
    };
  }

  if (count > COMFORTABLE_TREES) {
    separate(xs, ys);
    for (let rank = 0; rank < count; rank += 1) {
      const index = order[rank] as number;
      const old = placed[index] as ForestPlacement;
      placed[index] = { ...old, x: xs[rank] as number, y: ys[rank] as number };
    }
  }

  return placed;
}

/** 稳定序：无符号散列升序，id 字典序兜底。数据库行序从不参与。 */
function stableOrder(topicIds: readonly string[]): number[] {
  const order = topicIds.map((id, index) => {
    if (typeof id !== "string") throw new Error(`索引 ${index} 的主题 id 不能为空`);  // i18n-ignore: 诊断信息，只在开发期抛到控制台，不渲染到 UI
    return index;
  });
  return order.sort((left, right) => {
    const byHash = (hashOf(topicIds[left] as string) >>> 0) - (hashOf(topicIds[right] as string) >>> 0);
    if (byHash !== 0) return Math.sign(byHash);
    return (topicIds[left] as string) < (topicIds[right] as string)
      ? -1
      : (topicIds[left] as string) > (topicIds[right] as string)
        ? 1
        : 0;
  });
}

/** 黄金角螺旋：角度按 rank，半径按 sqrt(rank)，两者对同一主题恒定。 */
function position(rank: number, count: number, hash: number, xs: Float64Array, ys: Float64Array): void {
  if (count === 1) {
    xs[0] = CENTER_X;
    ys[0] = 0.46;
    return;
  }
  if (count === 2) {
    xs[rank] = rank === 0 ? 0.40 : 0.63;
    ys[rank] = rank === 0 ? 0.44 : 0.56;
    return;
  }
  const angleJitter = (((hash >>> 16) & 0xFF) / 255) * 0.30 - 0.15;
  const angle = rank * GOLDEN_ANGLE + angleJitter;
  const radialScale = 0.92 + (((hash >>> 24) & 0xFF) / 255) * 0.08;
  const rho = Math.sqrt((rank + 0.55) / count) * radialScale;
  xs[rank] = clamp(CENTER_X + Math.cos(angle) * RADIUS_X * rho, MIN_X, MAX_X);
  ys[rank] = clamp(CENTER_Y + Math.sin(angle) * RADIUS_Y * rho, MIN_Y, MAX_Y);
}

/** 把重叠的树干推开；配对遍历恒按 rank 序，所以结果确定。 */
function separate(xs: Float64Array, ys: Float64Array): void {
  const count = xs.length;
  for (let round = 0; round < SEPARATION_ROUNDS; round += 1) {
    let moved = false;
    for (let left = 0; left < count; left += 1) {
      for (let right = left + 1; right < count; right += 1) {
        let dx = (xs[right] as number) - (xs[left] as number);
        let dy = (ys[right] as number) - (ys[left] as number);
        let distance = Math.hypot(dx, dy);
        if (distance >= MIN_GAP) continue;
        if (distance < 0.000001) {
          dx = 1;
          dy = 0;
          distance = 1;
        }
        const push = (MIN_GAP - distance) / 2 + 0.0005;
        xs[left] = clamp((xs[left] as number) - (dx / distance) * push, MIN_X, MAX_X);
        ys[left] = clamp((ys[left] as number) - (dy / distance) * push, MIN_Y, MAX_Y);
        xs[right] = clamp((xs[right] as number) + (dx / distance) * push, MIN_X, MAX_X);
        ys[right] = clamp((ys[right] as number) + (dy / distance) * push, MIN_Y, MAX_Y);
        moved = true;
      }
    }
    if (!moved) return;
  }
}

function sizeFor(branches: number, density: number): number {
  const segments = Math.max(1, Math.min(SIZE_CAP_SEGMENTS, branches));
  const span = ((MAX_SIZE - MIN_SIZE) * (segments - 1)) / (SIZE_CAP_SEGMENTS - 1);
  return (MIN_SIZE + span) * density;
}

function densityFor(count: number): number {
  if (count <= 12) return 1;
  return Math.max(0.62, Math.min(1, Math.sqrt(12 / count)));
}

function toneFor(hash: number): number {
  return 0.80 + (((hash >>> 8) & 0xFF) / 255) * 0.35;
}

function seedFor(hash: number): number {
  const seed = hash & 0x7FFFFFFF;
  return seed === 0 ? 1 : seed;
}

/** FNV-1a：只吃 id 自己的字符，所以同一个 UUID 在每台设备上都是同一棵树。 */
function hashOf(value: string): number {
  let hash = 0x811C9DC5;
  for (let i = 0; i < value.length; i += 1) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
