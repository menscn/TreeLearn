// 设置数据访问（任务 T-2026-10-10-039）：只经契约客户端 `api` 取数/下发命令
// （api.md §6.6 配置：Provider/Key/模型、§5.12 模型分工与回落）。变更类请求一律带
// 客户端生成的 `commandId`（api.md §4）。Key 只写不读：除 `provider-key-save` 的请求体
// 外，本层从不把 Key 回传或持久化到浏览器存储。
import { api } from '../api/client';
import type { DeletedProvider, Model, ModelBudget, ModelResolution, ModelRoleKey, ModelRoles, ModelsRefreshed, Provider } from '../api/types';

export function newCommandId(): string {
  const webCrypto = globalThis.crypto;
  if (webCrypto !== undefined && typeof webCrypto.randomUUID === 'function') return webCrypto.randomUUID();
  return `cmd-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function loadProviders(): Promise<Provider[]> {
  return api.get<Provider[]>('/providers');
}

export function createProvider(input: { name: string; baseUrl: string; apiKey: string }): Promise<Provider> {
  return api.post<Provider>('/providers', { commandId: newCommandId(), name: input.name, baseUrl: input.baseUrl, apiKey: input.apiKey });
}

export function updateProvider(id: string, input: { name?: string; baseUrl?: string; enabled?: boolean }): Promise<Provider> {
  return api.put<Provider>(`/providers/${id}`, { commandId: newCommandId(), ...input });
}

export function deleteProvider(id: string): Promise<DeletedProvider> {
  return api.delete<DeletedProvider>(`/providers/${id}`, { commandId: newCommandId() });
}

export function saveProviderKey(id: string, apiKey: string): Promise<Provider> {
  return api.put<Provider>(`/providers/${id}/key`, { commandId: newCommandId(), apiKey });
}

export function deleteProviderKey(id: string): Promise<Provider> {
  return api.delete<Provider>(`/providers/${id}/key`, { commandId: newCommandId() });
}

export function loadProviderModels(id: string): Promise<Model[]> {
  return api.get<Model[]>(`/providers/${id}/models`);
}

// 契约 §6.6：刷新模型为 `POST /api/providers/{id}/models`（只增不删、精确去重）。
export function refreshProviderModels(id: string): Promise<ModelsRefreshed> {
  return api.post<ModelsRefreshed>(`/providers/${id}/models`, { commandId: newCommandId() });
}

export function loadModelRoles(): Promise<ModelRoles> {
  return api.get<ModelRoles>('/settings/model-roles');
}

export function loadModelResolution(): Promise<ModelResolution> {
  return api.get<ModelResolution>('/settings/model-resolution');
}

export function saveModelRoles(input: {
  roles: Record<ModelRoleKey, { providerId: string | null; modelId: string | null }>;
  budget: ModelBudget;
  suggestions: boolean;
}): Promise<ModelRoles> {
  return api.put<ModelRoles>('/settings/model-roles', { commandId: newCommandId(), roles: input.roles, budget: input.budget, suggestions: input.suggestions });
}
