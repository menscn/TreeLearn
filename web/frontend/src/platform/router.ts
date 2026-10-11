export type Route =
  | { name: 'home' } | { name: 'library' } | { name: 'stats' } | { name: 'topic'; id: string; branchId: string | null }
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
    const id = path.slice('/topic/'.length);
    if (id.length === 0) return { name: 'notFound', path };
    const branch = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search).get('branch');
    return { name: 'topic', id: decodeURIComponent(id), branchId: branch && branch.length > 0 ? branch : null };
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

export function navigate(to: string): void {
  if (window.location.pathname === to) return;
  window.history.pushState({}, '', to);
  // pushState 不触发 popstate；手动派发让订阅者重新读取位置。
  window.dispatchEvent(new PopStateEvent('popstate'));
}