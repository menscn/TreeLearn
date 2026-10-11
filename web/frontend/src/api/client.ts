import { ApiError, decodeErrorResponse } from './errors';

// 统一 HTTP 客户端：所有请求走同一入口，路径同源挂在 /api 前缀下（api.md §10）。
// 非 2xx 统一解码为 ApiError；网络失败转成安全文案（errors.networkUnreachable），
// 绝不把 fetch/网络库原始报错或后端异常文本透给 UI（rules/frontend.md 用户错误提示）。
// 禁止在页面/状态层散落裸 fetch。

export const API_PREFIX = '/api';

export function apiPath(path: string): string {
  if (path.startsWith(API_PREFIX)) return path;
  return `${API_PREFIX}${path.startsWith('/') ? path : `/${path}`}`;
}

async function readErrorBody(response: Response): Promise<unknown> {
  try {
    return JSON.parse(await response.text()) as unknown;
  } catch {
    return null;
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(apiPath(path), {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json; charset=UTF-8' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw ApiError.networkUnreachable();
  }

  if (!response.ok) {
    throw decodeErrorResponse(response.status, await readErrorBody(response));
  }

  const text = await response.text();
  if (text.length === 0) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    throw decodeErrorResponse(response.status, null);
  }
}

export const api = {
  get<T>(path: string) {
    return request<T>('GET', path);
  },
  post<T>(path: string, body: unknown) {
    return request<T>('POST', path, body);
  },
  put<T>(path: string, body: unknown) {
    return request<T>('PUT', path, body);
  },
  delete<T>(path: string, body?: unknown) {
    return request<T>('DELETE', path, body);
  },
};