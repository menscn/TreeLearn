import { useSyncExternalStore } from 'react';

import { currentLocation, matchRoute, subscribeLocation, type Route } from '../platform/router';

// 订阅 History 位置并解析为路由（含 `?branch=` 查询参数）。平台层提供订阅/读取原语，
// React 绑定留在 hooks；快照用 pathname+search，使仅查询参数变化也能触发重渲染。
export function useRoute(): Route {
  const location = useSyncExternalStore(subscribeLocation, currentLocation, () => '/');
  const queryIndex = location.indexOf('?');
  return matchRoute(queryIndex === -1 ? location : location.slice(0, queryIndex), queryIndex === -1 ? '' : location.slice(queryIndex));
}