// 学习库数据访问（任务 T-2026-10-10-044）：只经契约客户端 `api` 取数/下发命令
// （api.md §6.7 搜索、§6.8 统计、§6.3 分支恢复）。变更类请求一律带客户端生成的
// `commandId`（api.md §4）。响应按契约类型解码，形状漂移由 fixtures 测试守。
import { api } from '../api/client';
import type { Branch, SearchResult, Stats } from '../api/types';
import { newCommandId } from './topicData';

export function searchLibrary(query: string): Promise<SearchResult> {
  return api.get<SearchResult>(`/search?q=${encodeURIComponent(query)}`);
}

export function loadStats(timeZone: string, month: string): Promise<Stats> {
  return api.get<Stats>(`/stats?timeZone=${encodeURIComponent(timeZone)}&month=${encodeURIComponent(month)}`);
}

// 契约 §6.3：恢复归档子树为 `POST /api/branches/{id}/restore`。
export function restoreBranch(branchId: string): Promise<Branch> {
  return api.post<Branch>(`/branches/${branchId}/restore`, { commandId: newCommandId() });
}

// 可见性规则（api.md §5.3）：归档分支及其后代默认隐藏。以下为派生只读汇总的推导。
function archivedIds(nodes: Branch[]): Set<string> {
  return new Set(nodes.filter((node) => node.archivedAtMillis !== null).map((node) => node.id));
}

function hasArchivedAncestor(node: Branch, byId: Map<string, Branch>, archived: Set<string>): boolean {
  let parentId = node.parentBranchId;
  while (parentId !== null) {
    if (archived.has(parentId)) return true;
    parentId = byId.get(parentId)?.parentBranchId ?? null;
  }
  return false;
}

// 未读汇总：只累加可见（未归档且祖先未归档）分支的 `unreadCount`；折叠不改变总量。
export function unreadRollup(nodes: Branch[]): number {
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const archived = archivedIds(nodes);
  return nodes.reduce((total, node) => (node.archivedAtMillis === null && !hasArchivedAncestor(node, byId, archived) ? total + node.unreadCount : total), 0);
}

// 归档区列表：只列出归档子树的根（祖先已归档的节点不再单独出现）。
export function archivedRoots(nodes: Branch[]): Branch[] {
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const archived = archivedIds(nodes);
  return nodes.filter((node) => node.archivedAtMillis !== null && !hasArchivedAncestor(node, byId, archived));
}
