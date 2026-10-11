import type { ErrorCode } from './errors';

// 契约类型（TypeScript）。唯一权威：web/contracts/api.md。
// 逐字段对照该契约 §2（时间规则）、§3（错误封装）、§5（DTO）、§6（请求/响应体）
// 与 §7（SSE）。禁止在此另立语义：字段名、枚举 token、可空性与单位都必须与契约
// 一致；真实时间点一律为 UTC epoch 毫秒 number，字段名以 Millis 结尾；date-only
// （month "YYYY-MM"、day "YYYY-MM-DD"）保持字符串，消费者不做时区换算。
// 契约变更必须同改本文件、web/contracts/fixtures/** 与后端 DTO，并交
// contract-guardian 复核。为控制单文件规模，接口采用紧凑单行声明；语义不变。

export const MESSAGE_ROLES = ['USER', 'AI'] as const;
export type MessageRole = (typeof MESSAGE_ROLES)[number];

export const GENERATION_STATES = ['STAGED', 'VALIDATING', 'SUBMITTING', 'STREAMING_REASONING', 'STREAMING_ANSWER', 'STABLE', 'FAILED', 'INTERRUPTED', 'CANCELED'] as const;
export type GenerationState = (typeof GENERATION_STATES)[number];

export const PROMPT_PURPOSES = ['LEARNING_ANSWER', 'ROOT_TITLE', 'BRANCH_TITLE', 'BRANCH_SUMMARY', 'ANSWER_RECOMMENDATIONS', 'HOME_RECOMMENDATIONS', 'CONTEXT_COMPRESSION', 'MESSAGE_TRANSLATION'] as const;
export type PromptPurpose = (typeof PROMPT_PURPOSES)[number];

// 派生结果 kind 的落库 wireId（api.md §5.10 表格）：小写，对应 `ai_artifacts.kind`；
// 不含翻译（翻译无独立 kind 落库）。
export const DERIVED_ARTIFACT_KINDS = ['root_title', 'branch_title', 'branch_summary', 'answer_recommendations', 'home_recommendations', 'context_compression'] as const;
export type DerivedArtifactKind = (typeof DERIVED_ARTIFACT_KINDS)[number];

// 派生任务请求 kind（api.md §6.5 请求词汇表）：大写枚举，含不落库的 MESSAGE_TRANSLATION。
export const DERIVED_REQUEST_KINDS = ['ROOT_TITLE', 'BRANCH_TITLE', 'BRANCH_SUMMARY', 'ANSWER_RECOMMENDATIONS', 'HOME_RECOMMENDATIONS', 'CONTEXT_COMPRESSION', 'MESSAGE_TRANSLATION'] as const;
export type DerivedRequestKind = (typeof DERIVED_REQUEST_KINDS)[number];

export const ANNOTATION_CONTAINERS = ['PROSE', 'CELL'] as const;
export type AnnotationContainer = (typeof ANNOTATION_CONTAINERS)[number];

export const ANNOTATION_KINDS = ['HIGHLIGHT', 'NOTE'] as const;
export type AnnotationKind = (typeof ANNOTATION_KINDS)[number];

export const ATTACHMENT_PARSE_STATES = ['pending', 'parsed', 'parse_failed'] as const;
export type AttachmentParseState = (typeof ATTACHMENT_PARSE_STATES)[number];

export const SEARCH_FIELDS = ['TITLE', 'QUESTION', 'ANCHOR', 'ANSWER', 'REASONING', 'PARTIAL_ANSWER', 'PARTIAL_REASONING'] as const;
export type SearchField = (typeof SEARCH_FIELDS)[number];

export const WEB_SEARCH_ENGINES = ['ZHIPU', 'TAVILY', 'BOCHA'] as const;
export type WebSearchEngine = (typeof WEB_SEARCH_ENGINES)[number];

export const WEB_SEARCH_TIERS = ['THREE', 'FIVE', 'TEN'] as const;
export type WebSearchTier = (typeof WEB_SEARCH_TIERS)[number];

export const WEB_SEARCH_NOTICES = ['NONE', 'NOKEY'] as const;
export type WebSearchNotice = (typeof WEB_SEARCH_NOTICES)[number];

export const MODEL_ROLE_KEYS = ['CHAT', 'FAST', 'TRANSLATION'] as const;
export type ModelRoleKey = (typeof MODEL_ROLE_KEYS)[number];

export const MODEL_FALLBACKS = ['NONE', 'BUILTIN', 'EARLIEST', 'EMPTY'] as const;
export type ModelFallback = (typeof MODEL_FALLBACKS)[number];

export const MODEL_BUDGETS = ['OFF', 'AUTO', 'LOW', 'MID', 'HIGH'] as const;
export type ModelBudget = (typeof MODEL_BUDGETS)[number];

export const MODEL_NOTICES = ['NONE', 'FALLEN_BACK', 'NO_MODELS'] as const;
export type ModelNotice = (typeof MODEL_NOTICES)[number];

export interface Topic { id: string; lastVisitedBranchId: string | null; createdAtMillis: number; updatedAtMillis: number }
// 5.1b 主题概览行（`GET /api/topics`）：主题 + 主页卡片派生显示名。标题来自根分支标题，
// 缺失时回退根分支首条 USER 消息；两者皆无（非正常创建态）时 displayTitle 为 null。
export interface TopicSummary { topicId: string; rootTitle: string | null; rootQuestion: string | null; displayTitle: string | null; lastUsedAtMillis: number }
export interface BranchAnchor { startInclusive: number | null; endExclusive: number | null; quote: string | null; hash: string | null }
export interface BranchSource { parentBranchId: string; sourceMessageId: string; sourceRevisionId: string; anchor: BranchAnchor; contextSnapshotId: string }
export interface Branch { id: string; topicId: string; parentBranchId: string | null; source: BranchSource | null; title: string | null; createdAtMillis: number; updatedAtMillis: number; latestStableSequence: number; lastSeenStableSequence: number; unreadCount: number; collapsed: boolean; archivedAtMillis: number | null }
export interface BranchTree { topicId: string; nodes: Branch[] }
export interface AttachmentRef { attachmentId: string; displayName: string | null; present: boolean }
export interface AnswerRevision { id: string; messageId: string; revisionNo: number; reasoningContent: string; answerContent: string; model: string; thinkingEnabled: boolean; promptPolicyVersion: string; createdAtMillis: number }
export interface Message { id: string; branchId: string; role: MessageRole; ordinal: number; userContent: string | null; quotedText: string; createdAtMillis: number; activeRevisionId: string | null; activeOperationId: string | null; attachments: AttachmentRef[]; versions: AnswerRevision[] }
export interface Operation { id: string; commandId: string; branchId: string; messageId: string; promptPurpose: PromptPurpose; promptPolicyVersion: string; model: string; thinkingEnabled: boolean; state: GenerationState; partialReasoning: string; partialAnswer: string; createdAtMillis: number; errorCode: ErrorCode | null; sseUrl: string }
export interface Attachment { attachmentId: string; displayName: string | null; mimeType: string; sizeBytes: number; parseState: AttachmentParseState; present: boolean; referenceCount: number }
export interface AttachmentList { attachments: Attachment[]; usageBytes: number }
export interface Annotation { id: string; container: AnnotationContainer; proseIndex: number; tableIndex: number; rowIndex: number; colIndex: number; start: number; end: number; kind: AnnotationKind; body: string }
export interface AnnotationList { messageId: string; annotations: Annotation[] }
export interface Conversation { branchId: string; messages: Message[]; readingAnchor: ReadingAnchor | null; recommendations: string[] }
export interface Provider { id: string; name: string; baseUrl: string; enabled: boolean; hasKey: boolean; modelCount: number }
export interface Model { providerId: string; providerName: string; modelId: string }
export interface DeletedProvider { deletedProviderId: string }
export interface ModelsRefreshed { added: number; models: Model[] }
export interface ModelRoleEntry { providerId: string | null; modelId: string | null; fallback: ModelFallback }
export type ModelRolesMap = Record<ModelRoleKey, ModelRoleEntry>;
export interface ModelRoles { roles: ModelRolesMap; budget: ModelBudget; suggestions: boolean; notice: ModelNotice }
export interface ModelResolution { roles: ModelRolesMap }
export interface DerivedArtifact { kind: DerivedArtifactKind; purpose: PromptPurpose; scopeId: string; basedOnRevisionId: string | null; inputDigest: string; payload: { items: string[] }; createdAtMillis: number; stale: boolean }
export interface WebSearchService { engine: WebSearchEngine; hasKey: boolean }
export interface WebSearchSettings { searchEnabled: boolean; engine: WebSearchEngine; tier: WebSearchTier; services: WebSearchService[]; notice: WebSearchNotice }
export interface ReadingAnchor { messageId: string; revisionId: string | null; listItemOffset: number; viewportOffset: number }
export interface SearchHit { field: SearchField; topicId: string; branchId: string; messageId: string | null; messageOrdinal: number; revisionId: string | null; revisionNo: number; snippet: string; start: number; end: number }
export interface SearchResult { query: string; hits: SearchHit[] }
export interface Stats { empty: boolean; timeZone: string; month: string; totalQuestions: number; totalMessages: number; currentStreakDays: number; activeDays: string[]; monthCells: number[] }
export interface DeletionPreview { topicId: string; targetBranchId: string; wholeTopic: boolean; branchIds: string[]; branchCount: number; digest: string }
export interface TopicBootstrap { topic: Topic; rootBranch: Branch; userMessage: Message; aiMessage: Message; operation: Operation }
export interface TurnAccepted { userMessage: Message; aiMessage: Message; operation: Operation }
export interface MessageOperation { message: Message; operation: Operation }
export interface ForkResult { branch: Branch; source: BranchSource; bootstrap: TopicBootstrap | null }
export interface ReadyResponse { service: 'treelearn-web-backend'; status: 'ready' }

// 变更请求体（api.md §6）。变更类请求必须携带客户端生成的 commandId。
export interface CommandIdRequest { commandId: string }
export interface TopicCreateRequest { commandId: string; question: string; model: string; thinkingEnabled: boolean; quotedText?: string; attachmentId?: string | null }
export interface BranchMessageCreateRequest { commandId: string; text: string; quote?: string; attachmentId?: string | null; model: string; thinkingEnabled: boolean }
export interface ForkAnchor { startInclusive: number; endExclusive: number; quote: string; hash: string }
export interface ForkRequest { commandId: string; sourceMessageId: string; sourceRevisionId: string; anchor: ForkAnchor | null; question?: string | null; model?: string | null; thinkingEnabled?: boolean | null }
export interface MessageGenerationRequest { commandId: string; model: string; thinkingEnabled: boolean }
export interface BranchCollapseRequest { commandId: string; collapsed: boolean }
export interface BranchSeenRequest { commandId: string; seenThroughSequence: number }
export interface ReadingPositionRequest { commandId: string; messageId: string; revisionId?: string | null; listItemOffset: number; viewportOffset: number }
export interface DeletionConfirmRequest { commandId: string; digest: string }
export interface DerivedStartRequest { commandId: string; kind: DerivedRequestKind; scopeId: string; basedOnRevisionId?: string | null; model?: string | null }
export interface AnnotationCreateRequest { commandId: string; kind: AnnotationKind; container: AnnotationContainer; proseIndex?: number; tableIndex?: number; rowIndex?: number; colIndex?: number; start: number; end: number; body?: string }
export interface ProviderCreateRequest { commandId: string; name: string; baseUrl: string; apiKey?: string }
export interface ProviderUpdateRequest { commandId: string; name?: string; baseUrl?: string; enabled?: boolean }
export interface ProviderKeyRequest { commandId: string; apiKey: string }
export interface AttachmentUploadRequest { commandId: string; displayName: string; mimeType: string; contentBase64: string }
export interface ModelRoleUpdateEntry { providerId: string | null; modelId: string | null }
export interface ModelRolesUpdateRequest { commandId: string; roles?: Record<ModelRoleKey, ModelRoleUpdateEntry>; budget?: ModelBudget; suggestions?: boolean }
export interface WebSearchUpdateRequest { commandId: string; searchEnabled?: boolean; engine?: WebSearchEngine; tier?: WebSearchTier }
export interface WebSearchKeyRequest { commandId: string; apiKey: string }

export function isGenerationState(value: unknown): value is GenerationState {
  return typeof value === 'string' && (GENERATION_STATES as readonly string[]).includes(value);
}

export function isAnswerRevision(value: unknown): value is AnswerRevision {
  return typeof value === 'object' && value !== null && typeof (value as AnswerRevision).id === 'string';
}