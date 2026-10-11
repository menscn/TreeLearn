import type { TranslationKey } from '../i18n';

// 错误码与封装：唯一权威为 web/contracts/api.md §3（错误封装与状态码映射）。
// 本文件声明 42 个契约错误码、记录每个码的 HTTP 状态/retryable/params 键集合，
// 并把码映射到 i18n 文案键 errors.<code>。后端只返回机器可读码与安全插值参数，
// 面向用户的句子一律由前端按 i18n 解析（api.md §3.1；rules/frontend.md）。
// 契约变更必须同步本文件、web/contracts/fixtures/errors/** 与双语文案。

// 42 个契约错误码（api.md §3.3），SCREAMING_SNAKE token 逐字一致。
export type ErrorCode =
  | 'KEY_MISSING' | 'AUTH_INVALID' | 'BALANCE_UNAVAILABLE' | 'REQUEST_INVALID' | 'RATE_LIMITED' | 'PROVIDER_UNAVAILABLE' | 'NETWORK_INTERRUPTED' | 'PARSE_FAILED'
  | 'OPERATION_NOT_STABLE' | 'OPERATION_NOT_COMPLETABLE' | 'REVISION_IMMUTABLE' | 'MESSAGE_ROLE_MISMATCH' | 'ILLEGAL_STATE_TRANSITION' | 'ANCHOR_INVALID' | 'SEQUENCE_OUT_OF_ORDER'
  | 'PROMPT_POLICY_VERSION_REQUIRED' | 'PROMPT_POLICY_VERSION_MISMATCH' | 'CONTEXT_EXCEEDS_BUDGET' | 'CONTEXT_SOURCE_MISSING' | 'CONTEXT_SUMMARY_MISSING'
  | 'SNAPSHOT_PAYLOAD_INVALID' | 'SNAPSHOT_DIGEST_MISMATCH' | 'SNAPSHOT_SCHEMA_UNSUPPORTED'
  | 'BRANCH_BUSY' | 'DELETION_PLAN_EXPIRED' | 'SCOPE_UNRESOLVABLE' | 'TOPIC_ID_ALREADY_EXISTS' | 'BRANCH_NOT_ARCHIVABLE' | 'NOTE_BODY_REQUIRED' | 'SOURCE_MESSAGE_NOT_IN_TOPIC' | 'STORAGE_REFUSED'
  | 'PROVIDER_INVALID' | 'PROVIDER_NAME_TAKEN' | 'KEY_REQUIRED' | 'KEY_SAVE_FAILED' | 'NO_MODELS' | 'WEB_SEARCH_KEY_MISSING' | 'WEB_SEARCH_FAILED'
  | 'ATTACHMENT_TOO_LARGE' | 'ATTACHMENT_TYPE_UNSUPPORTED' | 'NOT_FOUND' | 'INTERNAL_ERROR';

export interface ErrorSpec { httpStatus: number; retryable: boolean; params: readonly string[] }

// api.md §3.3 词汇表：HTTP 状态、retryable 与 params 允许键集合。
export const ERROR_SPEC: Record<ErrorCode, ErrorSpec> = {
  KEY_MISSING: { httpStatus: 400, retryable: false, params: [] },
  AUTH_INVALID: { httpStatus: 400, retryable: false, params: ['httpStatus'] },
  BALANCE_UNAVAILABLE: { httpStatus: 400, retryable: false, params: ['httpStatus'] },
  REQUEST_INVALID: { httpStatus: 400, retryable: false, params: ['httpStatus'] },
  RATE_LIMITED: { httpStatus: 503, retryable: true, params: ['httpStatus'] },
  PROVIDER_UNAVAILABLE: { httpStatus: 503, retryable: true, params: ['httpStatus'] },
  NETWORK_INTERRUPTED: { httpStatus: 503, retryable: true, params: [] },
  PARSE_FAILED: { httpStatus: 503, retryable: true, params: ['channel'] },
  OPERATION_NOT_STABLE: { httpStatus: 409, retryable: false, params: [] },
  OPERATION_NOT_COMPLETABLE: { httpStatus: 404, retryable: false, params: [] },
  REVISION_IMMUTABLE: { httpStatus: 409, retryable: false, params: [] },
  MESSAGE_ROLE_MISMATCH: { httpStatus: 400, retryable: false, params: [] },
  ILLEGAL_STATE_TRANSITION: { httpStatus: 409, retryable: false, params: ['state'] },
  ANCHOR_INVALID: { httpStatus: 400, retryable: false, params: [] },
  SEQUENCE_OUT_OF_ORDER: { httpStatus: 409, retryable: false, params: [] },
  PROMPT_POLICY_VERSION_REQUIRED: { httpStatus: 500, retryable: false, params: [] },
  PROMPT_POLICY_VERSION_MISMATCH: { httpStatus: 500, retryable: false, params: ['purpose'] },
  CONTEXT_EXCEEDS_BUDGET: { httpStatus: 400, retryable: false, params: ['capacityChars', 'minAnswerChars'] },
  CONTEXT_SOURCE_MISSING: { httpStatus: 409, retryable: false, params: [] },
  CONTEXT_SUMMARY_MISSING: { httpStatus: 409, retryable: false, params: [] },
  SNAPSHOT_PAYLOAD_INVALID: { httpStatus: 500, retryable: false, params: [] },
  SNAPSHOT_DIGEST_MISMATCH: { httpStatus: 500, retryable: false, params: [] },
  SNAPSHOT_SCHEMA_UNSUPPORTED: { httpStatus: 500, retryable: false, params: ['schemaVersion'] },
  BRANCH_BUSY: { httpStatus: 409, retryable: false, params: ['branchId'] },
  DELETION_PLAN_EXPIRED: { httpStatus: 409, retryable: false, params: [] },
  SCOPE_UNRESOLVABLE: { httpStatus: 409, retryable: false, params: [] },
  TOPIC_ID_ALREADY_EXISTS: { httpStatus: 409, retryable: false, params: [] },
  BRANCH_NOT_ARCHIVABLE: { httpStatus: 400, retryable: false, params: [] },
  NOTE_BODY_REQUIRED: { httpStatus: 400, retryable: false, params: ['field'] },
  SOURCE_MESSAGE_NOT_IN_TOPIC: { httpStatus: 400, retryable: false, params: [] },
  STORAGE_REFUSED: { httpStatus: 500, retryable: true, params: [] },
  PROVIDER_INVALID: { httpStatus: 400, retryable: false, params: ['field'] },
  PROVIDER_NAME_TAKEN: { httpStatus: 409, retryable: false, params: [] },
  KEY_REQUIRED: { httpStatus: 400, retryable: false, params: ['scope'] },
  KEY_SAVE_FAILED: { httpStatus: 500, retryable: true, params: ['scope'] },
  NO_MODELS: { httpStatus: 400, retryable: false, params: [] },
  WEB_SEARCH_KEY_MISSING: { httpStatus: 400, retryable: false, params: ['engine'] },
  WEB_SEARCH_FAILED: { httpStatus: 503, retryable: true, params: ['engine'] },
  ATTACHMENT_TOO_LARGE: { httpStatus: 400, retryable: false, params: ['maxBytes'] },
  ATTACHMENT_TYPE_UNSUPPORTED: { httpStatus: 400, retryable: false, params: ['mimeType'] },
  NOT_FOUND: { httpStatus: 404, retryable: false, params: ['resource'] },
  INTERNAL_ERROR: { httpStatus: 500, retryable: true, params: [] },
};

export const ERROR_CODES = Object.keys(ERROR_SPEC) as readonly ErrorCode[];

const ERROR_CODE_SET: ReadonlySet<string> = new Set(ERROR_CODES);

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && ERROR_CODE_SET.has(value);
}

// 未知码兜底文案键（api.md §3.1：无对应条目时回落通用内部错误提示）。
export const FALLBACK_ERROR_KEY: TranslationKey = 'errors.internalError';

// 客户端本地网络失败文案键：错误发生在浏览器到本地服务之间，无服务端码。
export const NETWORK_UNREACHABLE_KEY: TranslationKey = 'errors.networkUnreachable';

export function errorI18nKey(code: ErrorCode): TranslationKey {
  return `errors.${code}` as TranslationKey;
}

export type ApiErrorParams = Readonly<Record<string, string | number | boolean>>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function sanitizeParams(value: unknown): ApiErrorParams {
  if (!isRecord(value)) return {};
  const safe: Record<string, string | number | boolean> = {};
  for (const [key, entry] of Object.entries(value)) {
    if (typeof entry === 'string' || typeof entry === 'number' || typeof entry === 'boolean') safe[key] = entry;
  }
  return safe;
}

export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly params: ApiErrorParams;
  readonly retryable: boolean;
  readonly httpStatus: number;
  readonly requestId: string;
  readonly i18nKey: TranslationKey;

  constructor(code: ErrorCode, params: ApiErrorParams, retryable: boolean, httpStatus: number, requestId: string, i18nKey: TranslationKey = errorI18nKey(code)) {
    super(code);
    this.name = 'ApiError';
    this.code = code;
    this.params = params;
    this.retryable = retryable;
    this.httpStatus = httpStatus;
    this.requestId = requestId;
    this.i18nKey = i18nKey;
  }

  static networkUnreachable(): ApiError {
    return new ApiError('NETWORK_INTERRUPTED', {}, true, 0, '', NETWORK_UNREACHABLE_KEY);
  }
}

// 把非 2xx 响应体解码为 ApiError；形状不符或未知码时回落 INTERNAL_ERROR。
// 只保留安全插值参数，绝不把原始响应体或堆栈带给 UI。
export function decodeErrorResponse(status: number, body: unknown): ApiError {
  if (isRecord(body)) {
    const envelope = body.error;
    if (isRecord(envelope) && isErrorCode(envelope.code)) {
      const spec = ERROR_SPEC[envelope.code];
      const retryable = typeof envelope.retryable === 'boolean' ? envelope.retryable : spec.retryable;
      const requestId = typeof body.requestId === 'string' ? body.requestId : '';
      return new ApiError(envelope.code, sanitizeParams(envelope.params), retryable, status, requestId);
    }
  }
  return new ApiError('INTERNAL_ERROR', {}, ERROR_SPEC.INTERNAL_ERROR.retryable, status, '', FALLBACK_ERROR_KEY);
}