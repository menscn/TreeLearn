export type Route =
  | { name: 'home' } | { name: 'library' } | { name: 'stats' }
  | { name: 'topic'; id: string; branchId: string | null }
  /** T-132：整棵树自己那一屏（Android `sink.openOverview`）。 */
  | { name: 'topicTree'; id: string; branchId: string | null }
  | { name: 'settings'; section: string | null } | { name: 'notFound'; path: string };

// 最小内部路由（任务 T-2026-10-10-030；T-2026-10-10-031 增加 `branch` 查询参数）：
// 只用 History API + 路径匹配，不引入路由依赖。`/topic/:id` 支持可选 `?branch=<id>`
// 指定当前分支；其余路径忽略查询参数。仍不支持嵌套路由、通配符与懒加载。
export function matchRoute(pathname: string, search = ''): Route {
  const path = pathname.length > 1 && pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
  if (path === '' || path === '/') return { name: 'home' };
  if (path === '/library') return { name: 'library' };
  if (path === '/stats') return { name: 'stats' };
  if (path.startsWith('/topic/')) {
    let id = path.slice('/topic/'.length);
    // T-132：`/topic/<id>/tree` 是总览屏；分支参数仍然带在查询串上。
    const tree = id.endsWith('/tree');
    if (tree) id = id.slice(0, -'/tree'.length);
    if (id.length === 0) return { name: 'notFound', path };
    const branch = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search).get('branch');
    const branchId = branch && branch.length > 0 ? branch : null;
    return { name: tree ? 'topicTree' : 'topic', id: decodeURIComponent(id), branchId };
  }
  if (path === '/settings') return { name: 'settings', section: null };
  if (path.startsWith('/settings/')) return { name: 'settings', section: path.slice('/settings/'.length) };
  return { name: 'notFound', path };
}

export function currentPath(): string {
  return window.location.pathname;
}

// 供 useRoute 的快照使用：pathname + search，使 `?branch=` 变化也能触发重渲染。
export function currentLocation(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function subscribeLocation(listener: () => void): () => void {
  window.addEventListener('popstate', listener);
  return () => window.removeEventListener('popstate', listener);
}

/**
 * T-130：每次跳转都把「从哪来」写进历史条目，使被打开的页面能按入口还原返回目标，
 * 而不是一律回主页（对齐 Android 的系统返回栈：回到打开它的那一屏）。
 */
export function navigate(to: string): void {
  // 完整位置（含 ?branch=）比较：只比 pathname 会让同一分支页内的分支切换被吞掉。
  if (currentLocation() === to) return;
  window.history.pushState({ from: currentLocation() }, '', to);
  // pushState 不触发 popstate；手动派发让订阅者重新读取位置。
  window.dispatchEvent(new PopStateEvent('popstate'));
}

/**
 * 打开当前页面的那个位置；直接输入地址（或刷新）进来时为 null —— 那时没有「上一屏」可言。
 * 只接受站内绝对路径，外部写入的 state 不参与路由。
 */
export function historyFrom(): string | null {
  const state = window.history.state as { from?: unknown } | null;
  return typeof state?.from === 'string' && state.from.startsWith('/') ? state.from : null;
}
