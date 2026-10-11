// 工作区数据访问（任务 T-2026-10-10-031）：只经契约客户端 `api` 取数/下发命令
// （api.md §6.2 主题详情、§6.3 分支树/消息/追问、§6.4 取消/重试）。变更类请求一律带
// 客户端生成的 `commandId`（api.md §4）。响应按契约类型解码，形状漂移由 fixtures 测试守。
import { api } from '../api/client';
import type { AnnotationList, Branch, BranchTree, TreeLayoutResponse, Conversation, Message, Operation, ReadingAnchor, Topic, TurnAccepted } from '../api/types';

export function newCommandId(): string {
  const webCrypto = globalThis.crypto;
  if (webCrypto !== undefined && typeof webCrypto.randomUUID === 'function') return webCrypto.randomUUID();
  return `cmd-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function loadTopic(topicId: string): Promise<Topic> {
  return api.get<Topic>(`/topics/${topicId}`);
}

export function loadBranchTree(topicId: string): Promise<BranchTree> {
  return api.get<BranchTree>(`/topics/${topicId}/branches`);
}

/** 契约 §6.11：C 树图几何（服务端按默认 scale=1.0 计算；currentBranchId 不在树内时服务端回落根分支）。 */
/** 契约 §6.3：`seen` 只前进（回退/越界 409 `SEQUENCE_OUT_OF_ORDER`）。 */
export function markBranchSeen(branchId: string, seenThroughSequence: number): Promise<unknown> {
  return api.post(`/branches/${branchId}/seen`, { commandId: newCommandId(), seenThroughSequence });
}

/** 契约 §6.3：阅读位置（语义锚点）；revisionId 为 null 表示跟随当前位置显示的版本。 */
export function saveReadingPosition(branchId: string, anchor: ReadingAnchor): Promise<unknown> {
  return api.put(`/branches/${branchId}/reading-position`, {
    commandId: newCommandId(),
    messageId: anchor.messageId,
    revisionId: anchor.revisionId,
    listItemOffset: anchor.listItemOffset,
    viewportOffset: anchor.viewportOffset,
  });
}

export function loadTreeLayout(topicId: string, currentBranchId: string | null): Promise<TreeLayoutResponse> {
  const query = currentBranchId === null ? '' : `?currentBranchId=${encodeURIComponent(currentBranchId)}`;
  return api.get<TreeLayoutResponse>(`/topics/${topicId}/tree-layout${query}`);
}

export function loadConversation(branchId: string): Promise<Conversation> {
  return api.get<Conversation>(`/branches/${branchId}/messages`);
}

// 根分支：`parentBranchId` 与 `source` 恰好在根分支同时为 null（api.md §5.2）。
export function rootBranch(tree: BranchTree): Branch | null {
  return tree.nodes.find((node) => node.parentBranchId === null && node.source === null) ?? tree.nodes.find((node) => node.parentBranchId === null) ?? null;
}

// 当前分支：显式查询参数优先，其次主题的 `lastVisitedBranchId`，最后回落到根分支。
export function resolveBranch(tree: BranchTree, preferredId: string | null): Branch | null {
  if (preferredId !== null) {
    const preferred = tree.nodes.find((node) => node.id === preferredId);
    if (preferred !== undefined) return preferred;
  }
  return rootBranch(tree);
}

// 契约 §5.6：操作的 `sseUrl` 固定为 `/api/operations/{id}/events`。
export function operationEventsUrl(operationId: string): string {
  return `/api/operations/${operationId}/events`;
}

export function sendBranchMessage(input: { branchId: string; text: string; quote: string; model: string; thinkingEnabled: boolean }): Promise<TurnAccepted> {
  return api.post<TurnAccepted>(`/branches/${input.branchId}/messages`, { commandId: newCommandId(), text: input.text, quote: input.quote, attachmentId: null, model: input.model, thinkingEnabled: input.thinkingEnabled });
}

export function cancelOperation(operationId: string): Promise<Operation> {
  return api.post<Operation>(`/operations/${operationId}/cancel`, { commandId: newCommandId() });
}

export function retryMessage(messageId: string, input: { model: string; thinkingEnabled: boolean }): Promise<{ message: Message; operation: Operation }> {
  return api.post<{ message: Message; operation: Operation }>(`/messages/${messageId}/retry`, { commandId: newCommandId(), model: input.model, thinkingEnabled: input.thinkingEnabled });
}
/** 契约 §6.10：读取时后端按显示文本 digest 清理失效批注后返回当前集。 */
export function loadAnnotations(messageId: string): Promise<AnnotationList> {
  return api.get<AnnotationList>(`/messages/${messageId}/annotations`);
}

export type AnnotationWriteInput = { messageId: string } & (
  | { kind: 'HIGHLIGHT'; container: 'PROSE'; proseIndex: number; start: number; end: number }
  | { kind: 'HIGHLIGHT'; container: 'CELL'; tableIndex: number; rowIndex: number; colIndex: number; start: number; end: number }
  | { kind: 'NOTE'; container: 'PROSE'; proseIndex: number; start: number; end: number; body: string }
);

export function createAnnotation(input: AnnotationWriteInput): Promise<AnnotationList> {
  const { messageId, ...request } = input;
  return api.post<AnnotationList>(`/messages/${messageId}/annotations`, { commandId: newCommandId(), ...request });
}

export function deleteAnnotation(annotationId: string): Promise<{ deletedAnnotationId: string }> {
  return api.delete<{ deletedAnnotationId: string }>(`/annotations/${annotationId}`, { commandId: newCommandId() });
}
