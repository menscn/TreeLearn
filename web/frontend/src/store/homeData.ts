// 主页数据访问（任务 T-2026-10-10-030）：只经契约客户端 `api` 取数（api.md §6.2 主题
// 列表、§5.12 模型回落），响应宽松解码：形状不符回落为空/不可用，不把原始响应体透给 UI。
// 真实联调在后续任务，本任务由 mock transport 驱动。
import { api } from '../api/client';
import type { TopicBootstrap, TopicSummary } from '../api/types';

export interface HomeConfig { usable: boolean; model: string | null }

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

// CHAT 角色 fallback=EMPTY 表示无任何可用模型；真实“无 Key”由服务端在 POST /api/topics 收口。
export async function loadHomeConfig(): Promise<HomeConfig> {
  const chat = asRecord(asRecord(asRecord(await api.get<unknown>('/settings/model-resolution'))?.roles)?.CHAT);
  const modelId = typeof chat?.modelId === 'string' ? chat.modelId : null;
  const usable = modelId !== null && chat?.fallback !== 'EMPTY';
  return { usable, model: usable ? modelId : null };
}

// 5.1b 概览行：`GET /api/topics` 返回带派生显示名的 TopicSummary（主题本身无名称）。
export async function loadRecentTopics(): Promise<TopicSummary[]> {
  const data = await api.get<unknown>('/topics');
  if (!Array.isArray(data)) return [];
  return data.filter((item): item is TopicSummary => asRecord(item) !== null && typeof (item as TopicSummary).topicId === 'string');
}

function newCommandId(): string {
  const webCrypto = globalThis.crypto;
  if (webCrypto !== undefined && typeof webCrypto.randomUUID === 'function') return webCrypto.randomUUID();
  return `cmd-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export async function submitFirstQuestion(input: { question: string; model: string; thinkingEnabled: boolean }): Promise<TopicBootstrap> {
  return api.post<TopicBootstrap>('/topics', {
    commandId: newCommandId(),
    question: input.question,
    model: input.model,
    thinkingEnabled: input.thinkingEnabled,
  });
}