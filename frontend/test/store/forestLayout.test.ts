import { describe, expect, it } from "vitest";
import {
  COMFORTABLE_TREES,
  MAX_SIZE,
  MIN_SIZE,
  forestLayout,
  type ForestPlacement
} from "../../src/store/forestLayout";

const IDS = ["t-alpha", "t-bravo", "t-charlie", "t-delta", "t-echo"];
const COUNTS: (number | null)[] = [3, 1, null, 9, 5];

function byId(placements: ForestPlacement[], ids: string[]): Map<string, ForestPlacement> {
  const map = new Map<string, ForestPlacement>();
  ids.forEach((id, index) => map.set(id, placements[index] as ForestPlacement));
  return map;
}

describe("forestLayout：与 Android ForestLayout 同常量同算法的地面", () => {
  it("位置只由 id 决定：输入顺序颠倒，同一 id 仍在同一处", () => {
    const forward = byId(forestLayout(IDS, COUNTS), IDS);
    const reversedIds = [...IDS].reverse();
    const reversedCounts = [...COUNTS].reverse();
    const backward = byId(forestLayout(reversedIds, reversedCounts), reversedIds);
    for (const id of IDS) {
      expect(backward.get(id)).toEqual(forward.get(id));
    }
  });

  it("重复调用逐值相同（稳定散列，不靠随机）", () => {
    expect(forestLayout(IDS, COUNTS)).toEqual(forestLayout(IDS, COUNTS));
  });

  it("只有一棵树时站在空地中心", () => {
    const [only] = forestLayout(["only-topic"], [4]);
    expect(only?.x).toBeCloseTo(0.5, 5);
    expect(only?.y).toBeCloseTo(0.46, 5);
  });

  it("大小随段枝数在上下限之间：1 段最小、9 段最大、更多不再长", () => {
    const [one, nine, capped, missing] = forestLayout(["a", "b", "c", "d"], [1, 9, 99, null]);
    expect(one?.size).toBeCloseTo(MIN_SIZE, 6);
    expect(nine?.size).toBeCloseTo(MAX_SIZE, 6);
    expect(capped?.size).toBeCloseTo(MAX_SIZE, 6);
    expect(missing?.size).toBeCloseTo(MIN_SIZE, 6);
  });

  it("拥挤时整体按密度收缩，但仍在上下限的比例内", () => {
    const count = 24;
    const ids = Array.from({ length: count }, (_, index) => `crowd-${index}`);
    const placements = forestLayout(ids, ids.map(() => 9));
    const density = Math.sqrt(12 / count);
    for (const placement of placements) {
      expect(placement.size).toBeCloseTo(MAX_SIZE * density, 6);
    }
  });

  it("超过舒适棵数后做分离：互不重叠且都留在空地内", () => {
    const count = COMFORTABLE_TREES + 8;
    const ids = Array.from({ length: count }, (_, index) => `sep-${index}`);
    const placements = forestLayout(ids, ids.map((_, index) => (index % 9) + 1));
    for (const placement of placements) {
      expect(placement.x).toBeGreaterThanOrEqual(0.1);
      expect(placement.x).toBeLessThanOrEqual(0.9);
      expect(placement.y).toBeGreaterThanOrEqual(0.13);
      expect(placement.y).toBeLessThanOrEqual(0.8);
    }
    for (let left = 0; left < placements.length; left += 1) {
      for (let right = left + 1; right < placements.length; right += 1) {
        const a = placements[left] as ForestPlacement;
        const b = placements[right] as ForestPlacement;
        const distance = Math.hypot(a.x - b.x, a.y - b.y);
        expect(distance, `${left}-${right}`).toBeGreaterThan(0.04);
      }
    }
  });

  it("tone 落在原型的树冠深浅区间，seed 恒为正", () => {
    for (const placement of forestLayout(IDS, COUNTS)) {
      expect(placement.tone).toBeGreaterThanOrEqual(0.8);
      expect(placement.tone).toBeLessThanOrEqual(1.15);
      expect(placement.seed).toBeGreaterThan(0);
    }
  });

  it("id 与段枝数对不齐时大声失败，而不是悄悄少画一棵", () => {
    expect(() => forestLayout(IDS, [1, 2])).toThrow();
  });
});
