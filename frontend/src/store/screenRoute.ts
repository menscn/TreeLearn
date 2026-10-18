import type { ScreenName } from "../platform/RuntimePort";

/**
 * 换屏与返回栈（Android 侧是单 Activity 内 swap()，这里对应为「一个栈 + 一个栈顶」）。
 *
 * 单实例的模块级 store 而不是 React context：本 App 只有一个外壳，把实例塞进 context
 * 只会多出一处「谁持有它」的问题；行为接线到阶段六时若真要多个导航栈，再换实现。
 */
export interface RouteState {
  stack: ScreenName[];
}

type Listener = () => void;

let state: RouteState = { stack: ["home"] };
const listeners = new Set<Listener>();

function emit(): void {
  for (const listener of listeners) listener();
}

export function subscribeRoute(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function routeSnapshot(): RouteState {
  return state;
}

export function currentScreen(): ScreenName {
  return state.stack[state.stack.length - 1] ?? "home";
}

export function navigate(screen: ScreenName): void {
  if (screen === currentScreen()) return;
  state = { stack: [...state.stack, screen] };
  emit();
}

export function goBack(): void {
  if (state.stack.length <= 1) return;
  state = { stack: state.stack.slice(0, -1) };
  emit();
}

export function canGoBack(): boolean {
  return state.stack.length > 1;
}

/** 底部标签那两支：换 tab 是把栈折叠成单屏，而不是在主页之上再压一屏（Android 侧同一做法）。 */
export function replaceStack(screen: ScreenName): void {
  state = { stack: [screen] };
  emit();
}
