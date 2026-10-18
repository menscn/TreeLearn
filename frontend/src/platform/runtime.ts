import type { RuntimePort } from "./RuntimePort";

let injected: RuntimePort | null = null;

/** 换宿主只改这一处：组件层不得判断自己跑在哪个适配器上（规格 §4）。 */
export function injectRuntime(port: RuntimePort): () => void {
  injected = port;
  return () => {
    if (injected === port) injected = null;
  };
}

export function runtime(): RuntimePort {
  if (injected === null) {
    throw new Error("runtime is not injected");
  }
  return injected;
}
