// @vitest-environment jsdom
import { act, createElement, type ReactNode, type RefObject } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useDockHeight } from "../../src/hooks/useDockHeight";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/**
 * 坞高测量（T-2026-10-25-085）。
 *
 * 输入坞悬浮后不再撑高布局，末行留白与底缘羽化全靠 `.chat-main` 上的 `--dock-h`。
 * 这里钉住三件事：首帧就写入、坞长高（引用条与 chips 出现）时跟着改、量不到高度时不写 0。
 */

interface FakeNode {
  offsetHeight: number;
  properties: [string, string][];
  style: { setProperty: (name: string, value: string) => void };
}

function fakeNode(height: number): FakeNode {
  const node: FakeNode = {
    offsetHeight: height,
    properties: [],
    style: {
      setProperty(name: string, value: string) {
        node.properties.push([name, value]);
      }
    }
  };
  return node;
}

interface StubObserver {
  callback: () => void;
  targets: unknown[];
}

let observers: StubObserver[] = [];
const realResizeObserver = globalThis.ResizeObserver;

function onlyObserver(): StubObserver {
  const entry = observers[0];
  if (entry === undefined) throw new Error("ResizeObserver 未被构造");
  return entry;
}

beforeEach(() => {
  observers = [];
  globalThis.ResizeObserver = class {
    constructor(callback: () => void) {
      observers.push({ callback, targets: [] });
    }

    observe(target: unknown): void {
      const entry = observers.at(-1);
      if (entry !== undefined) entry.targets.push(target);
    }

    disconnect(): void {
      observers = [];
    }
  } as unknown as typeof ResizeObserver;
});

afterEach(() => {
  globalThis.ResizeObserver = realResizeObserver;
});

async function render(node: ReactNode): Promise<Root> {
  const host = document.createElement("div");
  document.body.append(host);
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(node);
  });
  return root;
}

function probe(main: FakeNode, dock: FakeNode): ReactNode {
  const mainRef: RefObject<HTMLElement | null> = { current: main as unknown as HTMLElement };
  const dockRef: RefObject<HTMLElement | null> = { current: dock as unknown as HTMLElement };
  const Probe = () => {
    useDockHeight(mainRef, dockRef);
    return null;
  };
  return createElement(Probe);
}

describe("useDockHeight", () => {
  it("挂载首帧就把坞的实测高度写到定位父级上", async () => {
    const main = fakeNode(0);
    const dock = fakeNode(152);
    await render(probe(main, dock));
    expect(main.properties).toEqual([["--dock-h", "152px"]]);
  });

  it("观察的是坞本身，不是父级", async () => {
    const main = fakeNode(0);
    const dock = fakeNode(152);
    await render(probe(main, dock));
    expect(observers).toHaveLength(1);
    expect(onlyObserver().targets).toEqual([dock]);
  });

  it("引用条与 chips 让坞长高后，高度跟着改写", async () => {
    const main = fakeNode(0);
    const dock = fakeNode(152);
    await render(probe(main, dock));
    dock.offsetHeight = 236;
    await act(async () => {
      onlyObserver().callback();
    });
    expect(main.properties.at(-1)).toEqual(["--dock-h", "236px"]);
  });

  it("量不到高度时不写 0，免得末行留白凭空塌掉", async () => {
    const main = fakeNode(0);
    const dock = fakeNode(0);
    await render(probe(main, dock));
    expect(main.properties).toEqual([]);
  });

  it("没有 ResizeObserver 也不报错，只保留首帧值", async () => {
    // @ts-expect-error 故意移除宿主能力
    delete globalThis.ResizeObserver;
    const main = fakeNode(0);
    const dock = fakeNode(168);
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    await render(probe(main, dock));
    expect(main.properties).toEqual([["--dock-h", "168px"]]);
    expect(observers).toHaveLength(0);
    expect(spy).not.toHaveBeenCalled();
    spy.mockRestore();
  });
});
