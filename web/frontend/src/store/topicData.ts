// 工作区数据访问（任务 T-2026-10-10-031）：只经契约客户端 `api` 取数/下发命令
// （api.md §6.2 主题详情、§6.3 分支树/消息/追问、§6.4 取消/重试）。变更类请求一律带
// 客户端生成的 `commandId`（api.md §4）。响应按契约类型解码，形状漂移由 fixtures 测试守。
import { api } from '../api/client';
import type { AnnotationList, AnswerRevision, Attachment, AttachmentList, Branch, BranchTree, TreeLayoutResponse, Conversation, ForkAnchor, ForkResult, Message, MessageGenerationRequest, MessageOperation, Operation, ReadingAnchor, Topic, TurnAccepted } from '../api/types';

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

export function sendBranchMessage(input: { branchId: string; text: string; quote: string; attachmentId?: string | null; model: string; thinkingEnabled: boolean }): Promise<TurnAccepted> {
  return api.post<TurnAccepted>(`/branches/${input.branchId}/messages`, { commandId: newCommandId(), text: input.text, quote: input.quote, attachmentId: input.attachmentId ?? null, model: input.model, thinkingEnabled: input.thinkingEnabled });
}

export function cancelOperation(operationId: string): Promise<Operation> {
  return api.post<Operation>(`/operations/${operationId}/cancel`, { commandId: newCommandId() });
}

/** 契约 §6.3：整条/选文分叉（冻结快照 + 溯源）。anchor 为 null 即整条分叉；选文分叉的
 * hash 须为 quote 的小写十六进制 SHA-256（lib/digest.ts，与共享域 codec 同算法）。
 * 带 question 时原子接受首问，响应 bootstrap.rootBranch 即新建子分支。 */
export function forkBranch(input: {
  branchId: string;
  sourceMessageId: string;
  sourceRevisionId: string;
  anchor?: ForkAnchor | null;
  question?: string | null;
  model?: string | null;
  thinkingEnabled?: boolean | null;
}): Promise<ForkResult> {
  return api.post<ForkResult>(`/branches/${input.branchId}/forks`, {
    commandId: newCommandId(),
    sourceMessageId: input.sourceMessageId,
    sourceRevisionId: input.sourceRevisionId,
    anchor: input.anchor ?? null,
    question: input.question ?? null,
    model: input.model ?? null,
    thinkingEnabled: input.thinkingEnabled ?? null,
  });
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

/** 契约 §6.4：reset 对已显示稳定版本的位置发起新尝试（旧版本保留）。 */
export function resetMessage(messageId: string, request: MessageGenerationRequest): Promise<MessageOperation> {
  return api.post<MessageOperation>(`/messages/${messageId}/reset`, request);
}

/** 契约 §6.4：切换显示版本（仅移动指针），返回切换后的版本。 */
export function selectRevision(messageId: string, revisionId: string): Promise<AnswerRevision> {
  return api.post<AnswerRevision>(`/messages/${messageId}/revisions/${revisionId}/select`, { commandId: newCommandId() });
}

/** 契约 §6.5（T-052 切片）：启动 MESSAGE_TRANSLATION 派生，返回 operation（订阅 SSE 到终态）。scopeId 为 messageId，basedOnRevisionId 单独传递。 */
export function startTranslation(messageId: string, revisionId: string): Promise<Operation> {
  return api.post<Operation>('/derived', { commandId: newCommandId(), kind: 'MESSAGE_TRANSLATION', scopeId: messageId, basedOnRevisionId: revisionId });
}

/** 契约 §6.5：读取派生产物（payload 为解析后的 JSON；stale 为基线判定结果）。 */
export function loadDerivedArtifact(kind: string, scopeId: string, basedOnRevisionId: string): Promise<{ kind: string; scopeId: string; stale: boolean; payload: unknown }> {
  const query = `?kind=${encodeURIComponent(kind)}&scopeId=${encodeURIComponent(scopeId)}&basedOnRevisionId=${encodeURIComponent(basedOnRevisionId)}`;
  return api.get(`/derived${query}`);
}


/** 契约 §6.9：上传附件（内容寻址去重），返回含 attachmentId 的完整条目。 */
export function uploadAttachment(displayName: string, mimeType: string, contentBase64: string): Promise<Attachment> {
  return api.post<Attachment>('/attachments', { commandId: newCommandId(), displayName, mimeType, contentBase64 });
}

/** 契约 §6.9：文件管理页列表（含全局用量）。 */
export function listAttachments(): Promise<AttachmentList> {
  return api.get<AttachmentList>('/attachments');
}

/** 契约 §6.9：下载/预览内容（返回 base64，前端负责解码或落 objURL）。 */
export function loadAttachmentContent(attachmentId: string): Promise<{ attachmentId: string; displayName: string | null; mimeType: string; contentBase64: string }> {
  return api.get(`/attachments/${attachmentId}/content`);
}

/** 契约 §6.9：删除（无引用才允许；幂等）。 */
export function deleteAttachment(attachmentId: string): Promise<{ deletedAttachmentId: string }> {
  return api.delete<{ deletedAttachmentId: string }>(`/attachments/${attachmentId}`, { commandId: newCommandId() });
}

/** 契约 §6.9：清理孤儿（无引用附件）并返回清理明细。 */
export function cleanupOrphanAttachments(): Promise<{ deletedAttachmentIds: string[]; freedBytes: number }> {
  return api.post<{ deletedAttachmentIds: string[]; freedBytes: number }>('/attachments/cleanup-orphans', { commandId: newCommandId() });
}
