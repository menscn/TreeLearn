import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { ERROR_CODES, ERROR_SPEC, errorI18nKey, type ErrorCode } from '../errors';
import { OperationEventState } from '../sse';
import {
  ANNOTATION_CONTAINERS,
  ANNOTATION_KINDS,
  ATTACHMENT_PARSE_STATES,
  DERIVED_ARTIFACT_KINDS,
  DERIVED_REQUEST_KINDS,
  GENERATION_STATES,
  MESSAGE_ROLES,
  MODEL_BUDGETS,
  MODEL_FALLBACKS,
  MODEL_NOTICES,
  MODEL_ROLE_KEYS,
  PROMPT_PURPOSES,
  SEARCH_FIELDS,
  WEB_SEARCH_ENGINES,
  WEB_SEARCH_NOTICES,
  WEB_SEARCH_TIERS,
} from '../types';
import type {
  Annotation,
  AnnotationCreateRequest,
  AnnotationList,
  AnswerRevision,
  Attachment,
  AttachmentList,
  AttachmentUploadRequest,
  Branch,
  BranchCollapseRequest,
  BranchMessageCreateRequest,
  BranchSeenRequest,
  BranchTree,
  CommandIdRequest,
  Conversation,
  DeletedProvider,
  DeletionConfirmRequest,
  DeletionPreview,
  DerivedArtifact,
  DerivedStartRequest,
  ForkRequest,
  ForkResult,
  Message,
  MessageGenerationRequest,
  MessageOperation,
  Model,
  ModelResolution,
  ModelRoles,
  ModelRolesUpdateRequest,
  ModelsRefreshed,
  Operation,
  Provider,
  ProviderCreateRequest,
  ProviderKeyRequest,
  ProviderUpdateRequest,
  ReadingAnchor,
  ReadingPositionRequest,
  SearchResult,
  Stats,
  Topic,
  TopicBootstrap,
  TopicCreateRequest,
  TopicSummary,
  TurnAccepted,
  WebSearchKeyRequest,
  WebSearchSettings,
  WebSearchUpdateRequest,
} from '../types';

// Contract fixtures are the single shared samples (web/contracts/README.md). This
// test parses every one of the 104 fixtures and asserts the frontend contract
// types/state machine stay aligned with web/contracts/api.md. Fixtures are never
// copied into the frontend; they are read from the repo contracts directory.
// vitest runs with cwd = web/frontend, so the shared fixtures are one level up.
const FIXTURES_DIR = join(process.cwd(), '..', 'contracts', 'fixtures');

// --- minimal structural checker (fixtures-driven, no runtime schema dependency) ---

interface FieldSpec {
  type: 'string' | 'number' | 'boolean' | 'array' | 'object';
  nullable?: boolean;
  optional?: boolean;
  enum?: readonly string[];
  items?: FieldSpec;
  shape?: Shape;
}
type Shape = Record<string, FieldSpec>;

class ContractViolation extends Error {}

function fail(path: string, message: string): never {
  throw new ContractViolation(`${path}: ${message}`);
}

function asObject(value: unknown, path: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    fail(path, 'expected object');
  }
  return value as Record<string, unknown>;
}

function validateField(value: unknown, spec: FieldSpec, path: string): void {
  if (value === null) {
    if (!spec.nullable) fail(path, 'unexpected null');
    return;
  }
  switch (spec.type) {
    case 'string':
    case 'number':
    case 'boolean': {
      if (typeof value !== spec.type) fail(path, `expected ${spec.type}`);
      if (spec.enum && !spec.enum.includes(value as string)) {
        fail(path, `unexpected value "${String(value)}"`);
      }
      return;
    }
    case 'array': {
      if (!Array.isArray(value)) fail(path, 'expected array');
      if (spec.items) value.forEach((item, index) => validateField(item, spec.items!, `${path}[${index}]`));
      return;
    }
    case 'object': {
      if (!spec.shape) fail(path, 'missing object shape');
      validate(value, spec.shape, path);
      return;
    }
  }
}

function validate(value: unknown, shape: Shape, path: string): void {
  const object = asObject(value, path);
  for (const key of Object.keys(object)) {
    if (!(key in shape)) fail(`${path}.${key}`, 'field is not part of the contract type');
  }
  for (const [key, spec] of Object.entries(shape)) {
    const fieldPath = `${path}.${key}`;
    if (!(key in object)) {
      if (!spec.optional) fail(fieldPath, 'missing field');
      continue;
    }
    validateField(object[key], spec, fieldPath);
  }
}

// 运行时把 Shape 的键集与编译期键清单对齐；键清单又用 `KeyCompleteness` 与
// types.ts 的接口绑定，因此 Shape 与类型定义之间不会再各写一份而漂移。
function assertShapeKeys(shape: Shape, keys: readonly string[], label: string): void {
  const shapeKeys = Object.keys(shape).sort();
  const expected = [...keys].sort();
  if (shapeKeys.length !== expected.length || shapeKeys.some((key, index) => key !== expected[index])) {
    fail(label, `Shape keys [${shapeKeys.join(', ')}] do not match contract keys [${expected.join(', ')}]`);
  }
}

function loadFixtures(dir: string): { file: string; value: unknown }[] {
  const absolute = join(FIXTURES_DIR, dir);
  return readdirSync(absolute)
    .filter((name) => name.endsWith('.json'))
    .sort()
    .map((name) => ({
      file: `${dir}/${name}`,
      value: JSON.parse(readFileSync(join(absolute, name), 'utf8')) as unknown,
    }));
}

function check(file: string, run: () => void): void {
  try {
    run();
  } catch (error) {
    throw new Error(`[${file}] ${(error as Error).message}`, { cause: error });
  }
}

// --- compile-time linkage: each key list must cover every key of its TS type ---
// A missing key makes `KeyCompleteness<...>` resolve to `false`, so the const
// below fails to compile before the runtime check can drift.
type KeyCompleteness<T, K extends readonly (keyof T)[]> = Exclude<keyof T, K[number]> extends never ? true : false;

const TOPIC_KEYS = ['id', 'lastVisitedBranchId', 'createdAtMillis', 'updatedAtMillis'] as const satisfies readonly (keyof Topic)[];
const TOPIC_SUMMARY_KEYS = ['topicId', 'rootTitle', 'rootQuestion', 'displayTitle', 'lastUsedAtMillis'] as const satisfies readonly (keyof TopicSummary)[];
const BRANCH_KEYS = ['id', 'topicId', 'parentBranchId', 'source', 'title', 'createdAtMillis', 'updatedAtMillis', 'latestStableSequence', 'lastSeenStableSequence', 'unreadCount', 'collapsed', 'archivedAtMillis'] as const satisfies readonly (keyof Branch)[];
const BRANCH_TREE_KEYS = ['topicId', 'nodes'] as const satisfies readonly (keyof BranchTree)[];
const ANSWER_REVISION_KEYS = ['id', 'messageId', 'revisionNo', 'reasoningContent', 'answerContent', 'model', 'thinkingEnabled', 'promptPolicyVersion', 'createdAtMillis'] as const satisfies readonly (keyof AnswerRevision)[];
const MESSAGE_KEYS = ['id', 'branchId', 'role', 'ordinal', 'userContent', 'quotedText', 'createdAtMillis', 'activeRevisionId', 'activeOperationId', 'attachments', 'versions'] as const satisfies readonly (keyof Message)[];
const OPERATION_KEYS = ['id', 'commandId', 'branchId', 'messageId', 'promptPurpose', 'promptPolicyVersion', 'model', 'thinkingEnabled', 'state', 'partialReasoning', 'partialAnswer', 'createdAtMillis', 'errorCode', 'sseUrl'] as const satisfies readonly (keyof Operation)[];
const ATTACHMENT_KEYS = ['attachmentId', 'displayName', 'mimeType', 'sizeBytes', 'parseState', 'present', 'referenceCount'] as const satisfies readonly (keyof Attachment)[];
const ATTACHMENT_LIST_KEYS = ['attachments', 'usageBytes'] as const satisfies readonly (keyof AttachmentList)[];
const ANNOTATION_KEYS = ['id', 'container', 'proseIndex', 'tableIndex', 'rowIndex', 'colIndex', 'start', 'end', 'kind', 'body'] as const satisfies readonly (keyof Annotation)[];
const ANNOTATION_LIST_KEYS = ['messageId', 'annotations'] as const satisfies readonly (keyof AnnotationList)[];
const CONVERSATION_KEYS = ['branchId', 'messages', 'readingAnchor', 'recommendations'] as const satisfies readonly (keyof Conversation)[];
const PROVIDER_KEYS = ['id', 'name', 'baseUrl', 'enabled', 'hasKey', 'modelCount'] as const satisfies readonly (keyof Provider)[];
const MODEL_KEYS = ['providerId', 'providerName', 'modelId'] as const satisfies readonly (keyof Model)[];
const MODEL_ROLES_KEYS = ['roles', 'budget', 'suggestions', 'notice'] as const satisfies readonly (keyof ModelRoles)[];
const MODEL_RESOLUTION_KEYS = ['roles'] as const satisfies readonly (keyof ModelResolution)[];
const DERIVED_ARTIFACT_KEYS = ['kind', 'purpose', 'scopeId', 'basedOnRevisionId', 'inputDigest', 'payload', 'createdAtMillis', 'stale'] as const satisfies readonly (keyof DerivedArtifact)[];
const WEB_SEARCH_SETTINGS_KEYS = ['searchEnabled', 'engine', 'tier', 'services', 'notice'] as const satisfies readonly (keyof WebSearchSettings)[];
const READING_ANCHOR_KEYS = ['messageId', 'revisionId', 'listItemOffset', 'viewportOffset'] as const satisfies readonly (keyof ReadingAnchor)[];
const SEARCH_RESULT_KEYS = ['query', 'hits'] as const satisfies readonly (keyof SearchResult)[];
const STATS_KEYS = ['empty', 'timeZone', 'month', 'totalQuestions', 'totalMessages', 'currentStreakDays', 'activeDays', 'monthCells'] as const satisfies readonly (keyof Stats)[];
const DELETION_PREVIEW_KEYS = ['topicId', 'targetBranchId', 'wholeTopic', 'branchIds', 'branchCount', 'digest'] as const satisfies readonly (keyof DeletionPreview)[];
const TOPIC_BOOTSTRAP_KEYS = ['topic', 'rootBranch', 'userMessage', 'aiMessage', 'operation'] as const satisfies readonly (keyof TopicBootstrap)[];
const TURN_ACCEPTED_KEYS = ['userMessage', 'aiMessage', 'operation'] as const satisfies readonly (keyof TurnAccepted)[];
const MESSAGE_OPERATION_KEYS = ['message', 'operation'] as const satisfies readonly (keyof MessageOperation)[];
const FORK_RESULT_KEYS = ['branch', 'source', 'bootstrap'] as const satisfies readonly (keyof ForkResult)[];
const DELETED_PROVIDER_KEYS = ['deletedProviderId'] as const satisfies readonly (keyof DeletedProvider)[];
const MODELS_REFRESHED_KEYS = ['added', 'models'] as const satisfies readonly (keyof ModelsRefreshed)[];

const COMMAND_ID_KEYS = ['commandId'] as const satisfies readonly (keyof CommandIdRequest)[];
const TOPIC_CREATE_KEYS = ['commandId', 'question', 'model', 'thinkingEnabled', 'quotedText', 'attachmentId'] as const satisfies readonly (keyof TopicCreateRequest)[];
const BRANCH_MESSAGE_CREATE_KEYS = ['commandId', 'text', 'quote', 'attachmentId', 'model', 'thinkingEnabled'] as const satisfies readonly (keyof BranchMessageCreateRequest)[];
const FORK_KEYS = ['commandId', 'sourceMessageId', 'sourceRevisionId', 'anchor', 'question', 'model', 'thinkingEnabled'] as const satisfies readonly (keyof ForkRequest)[];
const MESSAGE_GENERATION_KEYS = ['commandId', 'model', 'thinkingEnabled'] as const satisfies readonly (keyof MessageGenerationRequest)[];
const BRANCH_COLLAPSE_KEYS = ['commandId', 'collapsed'] as const satisfies readonly (keyof BranchCollapseRequest)[];
const BRANCH_SEEN_KEYS = ['commandId', 'seenThroughSequence'] as const satisfies readonly (keyof BranchSeenRequest)[];
const READING_POSITION_KEYS = ['commandId', 'messageId', 'revisionId', 'listItemOffset', 'viewportOffset'] as const satisfies readonly (keyof ReadingPositionRequest)[];
const DELETION_CONFIRM_KEYS = ['commandId', 'digest'] as const satisfies readonly (keyof DeletionConfirmRequest)[];
const DERIVED_START_KEYS = ['commandId', 'kind', 'scopeId', 'basedOnRevisionId', 'model'] as const satisfies readonly (keyof DerivedStartRequest)[];
const ANNOTATION_CREATE_KEYS = ['commandId', 'kind', 'container', 'proseIndex', 'tableIndex', 'rowIndex', 'colIndex', 'start', 'end', 'body'] as const satisfies readonly (keyof AnnotationCreateRequest)[];
const PROVIDER_CREATE_KEYS = ['commandId', 'name', 'baseUrl', 'apiKey'] as const satisfies readonly (keyof ProviderCreateRequest)[];
const PROVIDER_UPDATE_KEYS = ['commandId', 'name', 'baseUrl', 'enabled'] as const satisfies readonly (keyof ProviderUpdateRequest)[];
const PROVIDER_KEY_KEYS = ['commandId', 'apiKey'] as const satisfies readonly (keyof ProviderKeyRequest)[];
const ATTACHMENT_UPLOAD_KEYS = ['commandId', 'displayName', 'mimeType', 'contentBase64'] as const satisfies readonly (keyof AttachmentUploadRequest)[];
const MODEL_ROLES_UPDATE_KEYS = ['commandId', 'roles', 'budget', 'suggestions'] as const satisfies readonly (keyof ModelRolesUpdateRequest)[];
const WEB_SEARCH_UPDATE_KEYS = ['commandId', 'searchEnabled', 'engine', 'tier'] as const satisfies readonly (keyof WebSearchUpdateRequest)[];
const WEB_SEARCH_KEY_KEYS = ['commandId', 'apiKey'] as const satisfies readonly (keyof WebSearchKeyRequest)[];

// Each const below fails to compile if its key list misses any key of the TS type.
const _TOPIC: KeyCompleteness<Topic, typeof TOPIC_KEYS> = true;
const _TOPIC_SUMMARY: KeyCompleteness<TopicSummary, typeof TOPIC_SUMMARY_KEYS> = true;
const _BRANCH: KeyCompleteness<Branch, typeof BRANCH_KEYS> = true;
const _BRANCH_TREE: KeyCompleteness<BranchTree, typeof BRANCH_TREE_KEYS> = true;
const _ANSWER_REVISION: KeyCompleteness<AnswerRevision, typeof ANSWER_REVISION_KEYS> = true;
const _MESSAGE: KeyCompleteness<Message, typeof MESSAGE_KEYS> = true;
const _OPERATION: KeyCompleteness<Operation, typeof OPERATION_KEYS> = true;
const _ATTACHMENT: KeyCompleteness<Attachment, typeof ATTACHMENT_KEYS> = true;
const _ATTACHMENT_LIST: KeyCompleteness<AttachmentList, typeof ATTACHMENT_LIST_KEYS> = true;
const _ANNOTATION: KeyCompleteness<Annotation, typeof ANNOTATION_KEYS> = true;
const _ANNOTATION_LIST: KeyCompleteness<AnnotationList, typeof ANNOTATION_LIST_KEYS> = true;
const _CONVERSATION: KeyCompleteness<Conversation, typeof CONVERSATION_KEYS> = true;
const _PROVIDER: KeyCompleteness<Provider, typeof PROVIDER_KEYS> = true;
const _MODEL: KeyCompleteness<Model, typeof MODEL_KEYS> = true;
const _MODEL_ROLES: KeyCompleteness<ModelRoles, typeof MODEL_ROLES_KEYS> = true;
const _MODEL_RESOLUTION: KeyCompleteness<ModelResolution, typeof MODEL_RESOLUTION_KEYS> = true;
const _DERIVED_ARTIFACT: KeyCompleteness<DerivedArtifact, typeof DERIVED_ARTIFACT_KEYS> = true;
const _WEB_SEARCH_SETTINGS: KeyCompleteness<WebSearchSettings, typeof WEB_SEARCH_SETTINGS_KEYS> = true;
const _READING_ANCHOR: KeyCompleteness<ReadingAnchor, typeof READING_ANCHOR_KEYS> = true;
const _SEARCH_RESULT: KeyCompleteness<SearchResult, typeof SEARCH_RESULT_KEYS> = true;
const _STATS: KeyCompleteness<Stats, typeof STATS_KEYS> = true;
const _DELETION_PREVIEW: KeyCompleteness<DeletionPreview, typeof DELETION_PREVIEW_KEYS> = true;
const _TOPIC_BOOTSTRAP: KeyCompleteness<TopicBootstrap, typeof TOPIC_BOOTSTRAP_KEYS> = true;
const _TURN_ACCEPTED: KeyCompleteness<TurnAccepted, typeof TURN_ACCEPTED_KEYS> = true;
const _MESSAGE_OPERATION: KeyCompleteness<MessageOperation, typeof MESSAGE_OPERATION_KEYS> = true;
const _FORK_RESULT: KeyCompleteness<ForkResult, typeof FORK_RESULT_KEYS> = true;
const _DELETED_PROVIDER: KeyCompleteness<DeletedProvider, typeof DELETED_PROVIDER_KEYS> = true;
const _MODELS_REFRESHED: KeyCompleteness<ModelsRefreshed, typeof MODELS_REFRESHED_KEYS> = true;
const _COMMAND_ID: KeyCompleteness<CommandIdRequest, typeof COMMAND_ID_KEYS> = true;
const _TOPIC_CREATE: KeyCompleteness<TopicCreateRequest, typeof TOPIC_CREATE_KEYS> = true;
const _BRANCH_MESSAGE_CREATE: KeyCompleteness<BranchMessageCreateRequest, typeof BRANCH_MESSAGE_CREATE_KEYS> = true;
const _FORK: KeyCompleteness<ForkRequest, typeof FORK_KEYS> = true;
const _MESSAGE_GENERATION: KeyCompleteness<MessageGenerationRequest, typeof MESSAGE_GENERATION_KEYS> = true;
const _BRANCH_COLLAPSE: KeyCompleteness<BranchCollapseRequest, typeof BRANCH_COLLAPSE_KEYS> = true;
const _BRANCH_SEEN: KeyCompleteness<BranchSeenRequest, typeof BRANCH_SEEN_KEYS> = true;
const _READING_POSITION: KeyCompleteness<ReadingPositionRequest, typeof READING_POSITION_KEYS> = true;
const _DELETION_CONFIRM: KeyCompleteness<DeletionConfirmRequest, typeof DELETION_CONFIRM_KEYS> = true;
const _DERIVED_START: KeyCompleteness<DerivedStartRequest, typeof DERIVED_START_KEYS> = true;
const _ANNOTATION_CREATE: KeyCompleteness<AnnotationCreateRequest, typeof ANNOTATION_CREATE_KEYS> = true;
const _PROVIDER_CREATE: KeyCompleteness<ProviderCreateRequest, typeof PROVIDER_CREATE_KEYS> = true;
const _PROVIDER_UPDATE: KeyCompleteness<ProviderUpdateRequest, typeof PROVIDER_UPDATE_KEYS> = true;
const _PROVIDER_KEY: KeyCompleteness<ProviderKeyRequest, typeof PROVIDER_KEY_KEYS> = true;
const _ATTACHMENT_UPLOAD: KeyCompleteness<AttachmentUploadRequest, typeof ATTACHMENT_UPLOAD_KEYS> = true;
const _MODEL_ROLES_UPDATE: KeyCompleteness<ModelRolesUpdateRequest, typeof MODEL_ROLES_UPDATE_KEYS> = true;
const _WEB_SEARCH_UPDATE: KeyCompleteness<WebSearchUpdateRequest, typeof WEB_SEARCH_UPDATE_KEYS> = true;
const _WEB_SEARCH_KEY: KeyCompleteness<WebSearchKeyRequest, typeof WEB_SEARCH_KEY_KEYS> = true;

const COMPLETENESS_ASSERTIONS: readonly boolean[] = [
  _TOPIC, _TOPIC_SUMMARY, _BRANCH, _BRANCH_TREE, _ANSWER_REVISION, _MESSAGE, _OPERATION, _ATTACHMENT, _ATTACHMENT_LIST,
  _ANNOTATION, _ANNOTATION_LIST, _CONVERSATION, _PROVIDER, _MODEL, _MODEL_ROLES, _MODEL_RESOLUTION,
  _DERIVED_ARTIFACT, _WEB_SEARCH_SETTINGS, _READING_ANCHOR, _SEARCH_RESULT, _STATS, _DELETION_PREVIEW,
  _TOPIC_BOOTSTRAP, _TURN_ACCEPTED, _MESSAGE_OPERATION, _FORK_RESULT, _DELETED_PROVIDER, _MODELS_REFRESHED, _COMMAND_ID, _TOPIC_CREATE, _BRANCH_MESSAGE_CREATE,
  _FORK, _MESSAGE_GENERATION, _BRANCH_COLLAPSE, _BRANCH_SEEN, _READING_POSITION, _DELETION_CONFIRM,
  _DERIVED_START, _ANNOTATION_CREATE, _PROVIDER_CREATE, _PROVIDER_UPDATE, _PROVIDER_KEY,
  _ATTACHMENT_UPLOAD, _MODEL_ROLES_UPDATE, _WEB_SEARCH_UPDATE, _WEB_SEARCH_KEY,
];

// --- DTO shapes (api.md §5) ---

const ANCHOR: Shape = {
  startInclusive: { type: 'number', nullable: true },
  endExclusive: { type: 'number', nullable: true },
  quote: { type: 'string', nullable: true },
  hash: { type: 'string', nullable: true },
};
const BRANCH_SOURCE: Shape = {
  parentBranchId: { type: 'string' },
  sourceMessageId: { type: 'string' },
  sourceRevisionId: { type: 'string' },
  anchor: { type: 'object', shape: ANCHOR },
  contextSnapshotId: { type: 'string' },
};
const BRANCH: Shape = {
  id: { type: 'string' },
  topicId: { type: 'string' },
  parentBranchId: { type: 'string', nullable: true },
  source: { type: 'object', shape: BRANCH_SOURCE, nullable: true },
  title: { type: 'string', nullable: true },
  createdAtMillis: { type: 'number' },
  updatedAtMillis: { type: 'number' },
  latestStableSequence: { type: 'number' },
  lastSeenStableSequence: { type: 'number' },
  unreadCount: { type: 'number' },
  collapsed: { type: 'boolean' },
  archivedAtMillis: { type: 'number', nullable: true },
};
const TOPIC: Shape = {
  id: { type: 'string' },
  lastVisitedBranchId: { type: 'string', nullable: true },
  createdAtMillis: { type: 'number' },
  updatedAtMillis: { type: 'number' },
};
const TOPIC_SUMMARY: Shape = {
  topicId: { type: 'string' },
  rootTitle: { type: 'string', nullable: true },
  rootQuestion: { type: 'string', nullable: true },
  displayTitle: { type: 'string', nullable: true },
  lastUsedAtMillis: { type: 'number' },
};
const ANSWER_REVISION: Shape = {
  id: { type: 'string' },
  messageId: { type: 'string' },
  revisionNo: { type: 'number' },
  reasoningContent: { type: 'string' },
  answerContent: { type: 'string' },
  model: { type: 'string' },
  thinkingEnabled: { type: 'boolean' },
  promptPolicyVersion: { type: 'string' },
  createdAtMillis: { type: 'number' },
};
const ATTACHMENT_REF: Shape = {
  attachmentId: { type: 'string' },
  displayName: { type: 'string', nullable: true },
  present: { type: 'boolean' },
};
const MESSAGE: Shape = {
  id: { type: 'string' },
  branchId: { type: 'string' },
  role: { type: 'string', enum: MESSAGE_ROLES },
  ordinal: { type: 'number' },
  userContent: { type: 'string', nullable: true },
  quotedText: { type: 'string' },
  createdAtMillis: { type: 'number' },
  activeRevisionId: { type: 'string', nullable: true },
  activeOperationId: { type: 'string', nullable: true },
  attachments: { type: 'array', items: { type: 'object', shape: ATTACHMENT_REF } },
  versions: { type: 'array', items: { type: 'object', shape: ANSWER_REVISION } },
};
const OPERATION: Shape = {
  id: { type: 'string' },
  commandId: { type: 'string' },
  branchId: { type: 'string' },
  messageId: { type: 'string' },
  promptPurpose: { type: 'string', enum: PROMPT_PURPOSES },
  promptPolicyVersion: { type: 'string' },
  model: { type: 'string' },
  thinkingEnabled: { type: 'boolean' },
  state: { type: 'string', enum: GENERATION_STATES },
  partialReasoning: { type: 'string' },
  partialAnswer: { type: 'string' },
  createdAtMillis: { type: 'number' },
  errorCode: { type: 'string', enum: ERROR_CODES, nullable: true },
  sseUrl: { type: 'string' },
};
const ATTACHMENT: Shape = {
  attachmentId: { type: 'string' },
  displayName: { type: 'string', nullable: true },
  mimeType: { type: 'string' },
  sizeBytes: { type: 'number' },
  parseState: { type: 'string', enum: ATTACHMENT_PARSE_STATES },
  present: { type: 'boolean' },
  referenceCount: { type: 'number' },
};
const ANNOTATION: Shape = {
  id: { type: 'string' },
  container: { type: 'string', enum: ANNOTATION_CONTAINERS },
  proseIndex: { type: 'number' },
  tableIndex: { type: 'number' },
  rowIndex: { type: 'number' },
  colIndex: { type: 'number' },
  start: { type: 'number' },
  end: { type: 'number' },
  kind: { type: 'string', enum: ANNOTATION_KINDS },
  body: { type: 'string' },
};
const PROVIDER: Shape = {
  id: { type: 'string' },
  name: { type: 'string' },
  baseUrl: { type: 'string' },
  enabled: { type: 'boolean' },
  hasKey: { type: 'boolean' },
  modelCount: { type: 'number' },
};
const MODEL: Shape = {
  providerId: { type: 'string' },
  providerName: { type: 'string' },
  modelId: { type: 'string' },
};
const MODEL_ROLE_ENTRY: Shape = {
  providerId: { type: 'string', nullable: true },
  modelId: { type: 'string', nullable: true },
  fallback: { type: 'string', enum: MODEL_FALLBACKS },
};
const MODEL_ROLES_MAP: Shape = Object.fromEntries(
  MODEL_ROLE_KEYS.map((role): [string, FieldSpec] => [role, { type: 'object', shape: MODEL_ROLE_ENTRY }]),
);
const DERIVED: Shape = {
  kind: { type: 'string', enum: DERIVED_ARTIFACT_KINDS },
  purpose: { type: 'string', enum: PROMPT_PURPOSES },
  scopeId: { type: 'string' },
  basedOnRevisionId: { type: 'string', nullable: true },
  inputDigest: { type: 'string' },
  payload: { type: 'object', shape: { items: { type: 'array', items: { type: 'string' } } } },
  createdAtMillis: { type: 'number' },
  stale: { type: 'boolean' },
};
const WEB_SEARCH_SERVICE: Shape = {
  engine: { type: 'string', enum: WEB_SEARCH_ENGINES },
  hasKey: { type: 'boolean' },
};
const WEB_SEARCH: Shape = {
  searchEnabled: { type: 'boolean' },
  engine: { type: 'string', enum: WEB_SEARCH_ENGINES },
  tier: { type: 'string', enum: WEB_SEARCH_TIERS },
  services: { type: 'array', items: { type: 'object', shape: WEB_SEARCH_SERVICE } },
  notice: { type: 'string', enum: WEB_SEARCH_NOTICES },
};
const READING_ANCHOR: Shape = {
  messageId: { type: 'string' },
  revisionId: { type: 'string', nullable: true },
  listItemOffset: { type: 'number' },
  viewportOffset: { type: 'number' },
};
const SEARCH_HIT: Shape = {
  field: { type: 'string', enum: SEARCH_FIELDS },
  topicId: { type: 'string' },
  branchId: { type: 'string' },
  messageId: { type: 'string', nullable: true },
  messageOrdinal: { type: 'number' },
  revisionId: { type: 'string', nullable: true },
  revisionNo: { type: 'number' },
  snippet: { type: 'string' },
  start: { type: 'number' },
  end: { type: 'number' },
};
const STATS: Shape = {
  empty: { type: 'boolean' },
  timeZone: { type: 'string' },
  month: { type: 'string' },
  totalQuestions: { type: 'number' },
  totalMessages: { type: 'number' },
  currentStreakDays: { type: 'number' },
  activeDays: { type: 'array', items: { type: 'string' } },
  monthCells: { type: 'array', items: { type: 'number' } },
};
const DELETION_PREVIEW: Shape = {
  topicId: { type: 'string' },
  targetBranchId: { type: 'string' },
  wholeTopic: { type: 'boolean' },
  branchIds: { type: 'array', items: { type: 'string' } },
  branchCount: { type: 'number' },
  digest: { type: 'string' },
};
const TOPIC_BOOTSTRAP: Shape = {
  topic: { type: 'object', shape: TOPIC },
  rootBranch: { type: 'object', shape: BRANCH },
  userMessage: { type: 'object', shape: MESSAGE },
  aiMessage: { type: 'object', shape: MESSAGE },
  operation: { type: 'object', shape: OPERATION },
};

const DTO_SHAPES: Record<string, Shape> = {
  annotation: ANNOTATION,
  'annotation-list': { messageId: { type: 'string' }, annotations: { type: 'array', items: { type: 'object', shape: ANNOTATION } } },
  'answer-revision': ANSWER_REVISION,
  attachment: ATTACHMENT,
  'attachment-list': { attachments: { type: 'array', items: { type: 'object', shape: ATTACHMENT } }, usageBytes: { type: 'number' } },
  branch: BRANCH,
  'branch-tree': { topicId: { type: 'string' }, nodes: { type: 'array', items: { type: 'object', shape: BRANCH } } },
  conversation: {
    branchId: { type: 'string' },
    messages: { type: 'array', items: { type: 'object', shape: MESSAGE } },
    readingAnchor: { type: 'object', shape: READING_ANCHOR, nullable: true },
    recommendations: { type: 'array', items: { type: 'string' } },
  },
  'deletion-preview': DELETION_PREVIEW,
  'deleted-provider': { deletedProviderId: { type: 'string' } },
  'derived-artifact': DERIVED,
  'fork-result': { branch: { type: 'object', shape: BRANCH }, source: { type: 'object', shape: BRANCH_SOURCE }, bootstrap: { type: 'object', shape: TOPIC_BOOTSTRAP, nullable: true } },
  message: MESSAGE,
  model: MODEL,
  'models-refreshed': { added: { type: 'number' }, models: { type: 'array', items: { type: 'object', shape: MODEL } } },
  'model-resolution': { roles: { type: 'object', shape: MODEL_ROLES_MAP } },
  'model-roles': {
    roles: { type: 'object', shape: MODEL_ROLES_MAP },
    budget: { type: 'string', enum: MODEL_BUDGETS },
    suggestions: { type: 'boolean' },
    notice: { type: 'string', enum: MODEL_NOTICES },
  },
  operation: OPERATION,
  provider: PROVIDER,
  'reading-anchor': READING_ANCHOR,
  'search-result': { query: { type: 'string' }, hits: { type: 'array', items: { type: 'object', shape: SEARCH_HIT } } },
  stats: STATS,
  topic: TOPIC,
  'topic-summary': TOPIC_SUMMARY,
  'topic-bootstrap': TOPIC_BOOTSTRAP,
  'turn-accepted': { userMessage: { type: 'object', shape: MESSAGE }, aiMessage: { type: 'object', shape: MESSAGE }, operation: { type: 'object', shape: OPERATION } },
  'message-operation': { message: { type: 'object', shape: MESSAGE }, operation: { type: 'object', shape: OPERATION } },
  'web-search-settings': WEB_SEARCH,
};

const DTO_KEYS: Record<string, readonly string[]> = {
  annotation: ANNOTATION_KEYS,
  'annotation-list': ANNOTATION_LIST_KEYS,
  'answer-revision': ANSWER_REVISION_KEYS,
  attachment: ATTACHMENT_KEYS,
  'attachment-list': ATTACHMENT_LIST_KEYS,
  branch: BRANCH_KEYS,
  'branch-tree': BRANCH_TREE_KEYS,
  conversation: CONVERSATION_KEYS,
  'deletion-preview': DELETION_PREVIEW_KEYS,
  'deleted-provider': DELETED_PROVIDER_KEYS,
  'derived-artifact': DERIVED_ARTIFACT_KEYS,
  'fork-result': FORK_RESULT_KEYS,
  message: MESSAGE_KEYS,
  model: MODEL_KEYS,
  'models-refreshed': MODELS_REFRESHED_KEYS,
  'model-resolution': MODEL_RESOLUTION_KEYS,
  'model-roles': MODEL_ROLES_KEYS,
  operation: OPERATION_KEYS,
  provider: PROVIDER_KEYS,
  'reading-anchor': READING_ANCHOR_KEYS,
  'search-result': SEARCH_RESULT_KEYS,
  stats: STATS_KEYS,
  topic: TOPIC_KEYS,
  'topic-summary': TOPIC_SUMMARY_KEYS,
  'topic-bootstrap': TOPIC_BOOTSTRAP_KEYS,
  'turn-accepted': TURN_ACCEPTED_KEYS,
  'message-operation': MESSAGE_OPERATION_KEYS,
  'web-search-settings': WEB_SEARCH_SETTINGS_KEYS,
};

// --- command request shapes (api.md §6) ---

const FORK_ANCHOR: Shape = {
  startInclusive: { type: 'number' },
  endExclusive: { type: 'number' },
  quote: { type: 'string' },
  hash: { type: 'string' },
};
const MODEL_ROLE_UPDATE_ENTRY: Shape = {
  providerId: { type: 'string', nullable: true },
  modelId: { type: 'string', nullable: true },
};
const MODEL_ROLES_UPDATE_MAP: Shape = Object.fromEntries(
  MODEL_ROLE_KEYS.map((role): [string, FieldSpec] => [role, { type: 'object', shape: MODEL_ROLE_UPDATE_ENTRY }]),
);

const COMMAND_ID_SHAPE: Shape = { commandId: { type: 'string' } };
const ANNOTATION_CREATE_SHAPE: Shape = {
  commandId: { type: 'string' },
  kind: { type: 'string', enum: ANNOTATION_KINDS },
  container: { type: 'string', enum: ANNOTATION_CONTAINERS },
  proseIndex: { type: 'number', optional: true },
  tableIndex: { type: 'number', optional: true },
  rowIndex: { type: 'number', optional: true },
  colIndex: { type: 'number', optional: true },
  start: { type: 'number' },
  end: { type: 'number' },
  body: { type: 'string', optional: true },
};
const READING_POSITION_SHAPE: Shape = {
  commandId: { type: 'string' },
  messageId: { type: 'string' },
  revisionId: { type: 'string', nullable: true, optional: true },
  listItemOffset: { type: 'number' },
  viewportOffset: { type: 'number' },
};
const MESSAGE_GENERATION_SHAPE: Shape = { commandId: { type: 'string' }, model: { type: 'string' }, thinkingEnabled: { type: 'boolean' } };
const DELETION_CONFIRM_SHAPE: Shape = { commandId: { type: 'string' }, digest: { type: 'string' } };
const FORK_SHAPE: Shape = {
  commandId: { type: 'string' },
  sourceMessageId: { type: 'string' },
  sourceRevisionId: { type: 'string' },
  anchor: { type: 'object', shape: FORK_ANCHOR, nullable: true },
  question: { type: 'string', nullable: true, optional: true },
  model: { type: 'string', nullable: true, optional: true },
  thinkingEnabled: { type: 'boolean', nullable: true, optional: true },
};

const COMMAND_SHAPES: Record<string, Shape> = {
  'annotation-create-highlight': ANNOTATION_CREATE_SHAPE,
  'annotation-create-note': ANNOTATION_CREATE_SHAPE,
  'annotation-delete': COMMAND_ID_SHAPE,
  'attachment-upload': { commandId: { type: 'string' }, displayName: { type: 'string' }, mimeType: { type: 'string' }, contentBase64: { type: 'string' } },
  'branch-archive': COMMAND_ID_SHAPE,
  'branch-collapse': { commandId: { type: 'string' }, collapsed: { type: 'boolean' } },
  'branch-delete': DELETION_CONFIRM_SHAPE,
  'branch-message-create': {
    commandId: { type: 'string' },
    text: { type: 'string' },
    quote: { type: 'string', optional: true },
    attachmentId: { type: 'string', nullable: true, optional: true },
    model: { type: 'string' },
    thinkingEnabled: { type: 'boolean' },
  },
  'branch-reading-position': READING_POSITION_SHAPE,
  'branch-reading-position-follow': READING_POSITION_SHAPE,
  'branch-seen': { commandId: { type: 'string' }, seenThroughSequence: { type: 'number' } },
  'derived-start': {
    commandId: { type: 'string' },
    kind: { type: 'string', enum: DERIVED_REQUEST_KINDS },
    scopeId: { type: 'string' },
    basedOnRevisionId: { type: 'string', nullable: true, optional: true },
    model: { type: 'string', nullable: true, optional: true },
  },
  'files-cleanup': COMMAND_ID_SHAPE,
  'fork-selection': FORK_SHAPE,
  'fork-whole': FORK_SHAPE,
  'message-reset': MESSAGE_GENERATION_SHAPE,
  'message-retry': MESSAGE_GENERATION_SHAPE,
  'model-roles-update': {
    commandId: { type: 'string' },
    roles: { type: 'object', shape: MODEL_ROLES_UPDATE_MAP, optional: true },
    budget: { type: 'string', enum: MODEL_BUDGETS, optional: true },
    suggestions: { type: 'boolean', optional: true },
  },
  'operation-cancel': COMMAND_ID_SHAPE,
  'provider-create': { commandId: { type: 'string' }, name: { type: 'string' }, baseUrl: { type: 'string' }, apiKey: { type: 'string', optional: true } },
  'provider-key-save': { commandId: { type: 'string' }, apiKey: { type: 'string' } },
  'provider-models-refresh': COMMAND_ID_SHAPE,
  'provider-update': { commandId: { type: 'string' }, name: { type: 'string', optional: true }, baseUrl: { type: 'string', optional: true }, enabled: { type: 'boolean', optional: true } },
  'revision-select': COMMAND_ID_SHAPE,
  'topic-create': { commandId: { type: 'string' }, question: { type: 'string' }, model: { type: 'string' }, thinkingEnabled: { type: 'boolean' }, quotedText: { type: 'string', optional: true }, attachmentId: { type: 'string', nullable: true, optional: true } },
  'topic-delete': DELETION_CONFIRM_SHAPE,
  'web-search-key-save': { commandId: { type: 'string' }, apiKey: { type: 'string' } },
  'web-search-update': { commandId: { type: 'string' }, searchEnabled: { type: 'boolean', optional: true }, engine: { type: 'string', enum: WEB_SEARCH_ENGINES, optional: true }, tier: { type: 'string', enum: WEB_SEARCH_TIERS, optional: true } },
};

const COMMAND_KEYS: Record<string, readonly string[]> = {
  'annotation-create-highlight': ANNOTATION_CREATE_KEYS,
  'annotation-create-note': ANNOTATION_CREATE_KEYS,
  'annotation-delete': COMMAND_ID_KEYS,
  'attachment-upload': ATTACHMENT_UPLOAD_KEYS,
  'branch-archive': COMMAND_ID_KEYS,
  'branch-collapse': BRANCH_COLLAPSE_KEYS,
  'branch-delete': DELETION_CONFIRM_KEYS,
  'branch-message-create': BRANCH_MESSAGE_CREATE_KEYS,
  'branch-reading-position': READING_POSITION_KEYS,
  'branch-reading-position-follow': READING_POSITION_KEYS,
  'branch-seen': BRANCH_SEEN_KEYS,
  'derived-start': DERIVED_START_KEYS,
  'files-cleanup': COMMAND_ID_KEYS,
  'fork-selection': FORK_KEYS,
  'fork-whole': FORK_KEYS,
  'message-reset': MESSAGE_GENERATION_KEYS,
  'message-retry': MESSAGE_GENERATION_KEYS,
  'model-roles-update': MODEL_ROLES_UPDATE_KEYS,
  'operation-cancel': COMMAND_ID_KEYS,
  'provider-create': PROVIDER_CREATE_KEYS,
  'provider-key-save': PROVIDER_KEY_KEYS,
  'provider-models-refresh': COMMAND_ID_KEYS,
  'provider-update': PROVIDER_UPDATE_KEYS,
  'revision-select': COMMAND_ID_KEYS,
  'topic-create': TOPIC_CREATE_KEYS,
  'topic-delete': DELETION_CONFIRM_KEYS,
  'web-search-key-save': WEB_SEARCH_KEY_KEYS,
  'web-search-update': WEB_SEARCH_UPDATE_KEYS,
};

describe('contract fixtures', () => {
  it('keeps every key list complete against types.ts', () => {
    expect(COMPLETENESS_ASSERTIONS).toHaveLength(46);
    expect(COMPLETENESS_ASSERTIONS.every((complete) => complete === true)).toBe(true);
  });

  it('consumes every fixture in the shared contracts directory', () => {
    const total =
      loadFixtures('dtos').length +
      loadFixtures('commands').length +
      loadFixtures('errors').length +
      loadFixtures('sse').length;
    expect(total).toBe(104);
  });

  it('maps every DTO fixture to a contract shape', () => {
    const fixtures = loadFixtures('dtos');
    expect(fixtures).toHaveLength(28);
    expect(Object.keys(DTO_SHAPES)).toHaveLength(28);
    for (const fixture of fixtures) {
      const name = fixture.file.replace('dtos/', '').replace('.json', '');
      check(fixture.file, () => {
        const shape = DTO_SHAPES[name];
        const keys = DTO_KEYS[name];
        if (!shape || !keys) fail(name, `no contract registered for ${name}`);
        assertShapeKeys(shape, keys, name);
        validate(fixture.value, shape, name);
      });
    }
    for (const name of Object.keys(DTO_SHAPES)) {
      expect(fixtures.some((f) => f.file === `dtos/${name}.json`), `orphan DTO shape ${name}`).toBe(true);
    }
  });

  it('maps every command fixture to a request body shape', () => {
    const fixtures = loadFixtures('commands');
    expect(fixtures).toHaveLength(28);
    expect(Object.keys(COMMAND_SHAPES)).toHaveLength(28);
    for (const fixture of fixtures) {
      const name = fixture.file.replace('commands/', '').replace('.json', '');
      check(fixture.file, () => {
        const shape = COMMAND_SHAPES[name];
        const keys = COMMAND_KEYS[name];
        if (!shape || !keys) fail(name, `no request registered for ${name}`);
        assertShapeKeys(shape, keys, name);
        const wrapper = asObject(fixture.value, name);
        const method = wrapper.method;
        const path = wrapper.path;
        if (typeof method !== 'string' || method.length === 0) fail(`${name}.method`, 'expected HTTP method');
        if (typeof path !== 'string' || !path.startsWith('/api/')) fail(`${name}.path`, 'expected /api/ path');
        if (!('body' in wrapper)) fail(`${name}.body`, 'missing request body');
        validate(wrapper.body, shape, 'body');
      });
    }
    for (const name of Object.keys(COMMAND_SHAPES)) {
      expect(fixtures.some((f) => f.file === `commands/${name}.json`), `orphan request shape ${name}`).toBe(true);
    }
  });

  it('matches every error fixture against the contract error table', () => {
    const fixtures = loadFixtures('errors');
    expect(fixtures).toHaveLength(42);
    for (const fixture of fixtures) {
      const name = fixture.file.replace('errors/', '').replace('.json', '');
      check(fixture.file, () => {
        const wrapper = asObject(fixture.value, name);
        const error = asObject(wrapper.error, `${name}.error`);
        const code = error.code;
        if (typeof code !== 'string') fail(`${name}.error.code`, 'expected string');
        const spec = ERROR_SPEC[code as ErrorCode];
        if (!spec) fail(`${name}.error.code`, `unknown error code ${code}`);
        if (error.retryable !== spec.retryable) {
          fail(`${name}.error.retryable`, `expected ${spec.retryable} for ${code}`);
        }
        const params = asObject(error.params, `${name}.error.params`);
        expect(Object.keys(params).sort(), `${name} params drift`).toEqual([...spec.params].sort());
        if (typeof wrapper.requestId !== 'string') fail(`${name}.requestId`, 'expected string');
        expect(errorI18nKey(code as ErrorCode)).toBe(`errors.${code}`);
      });
    }
  });

  it('replays every SSE fixture through the operation event state machine', () => {
    const fixtures = loadFixtures('sse');
    expect(fixtures).toHaveLength(6);
    const seen = new Set<string>();
    for (const fixture of fixtures) {
      const name = fixture.file.replace('sse/', '').replace('.json', '');
      check(fixture.file, () => {
        const wrapper = asObject(fixture.value, name);
        const operationId = wrapper.operationId;
        if (typeof operationId !== 'string') fail(`${name}.operationId`, 'expected string');
        const lastEventId = typeof wrapper.lastEventId === 'string' ? wrapper.lastEventId : null;
        const frames = Array.isArray(wrapper.frames) ? wrapper.frames : fail(`${name}.frames`, 'expected array');
        const state = new OperationEventState(operationId, {}, lastEventId);
        const applied: number[] = [];
        const ignored = new Map<string, string>();
        for (const rawFrame of frames) {
          const frame = asObject(rawFrame, `${name}.frames[]`);
          const result = state.apply({
            id: typeof frame.id === 'string' ? frame.id : undefined,
            event: typeof frame.event === 'string' ? frame.event : '',
            data: frame.data,
          });
          if (result.applied && result.seq !== undefined) applied.push(result.seq);
          if (!result.applied) ignored.set(String(frame.id), result.reason ?? 'unknown');
        }
        seen.add(name);
        if (Array.isArray(wrapper.expectedAppliedSeqs)) {
          expect(applied, `${name} applied seqs`).toEqual(wrapper.expectedAppliedSeqs);
          const expectedIgnored = Array.isArray(wrapper.ignored) ? wrapper.ignored : [];
          for (const entry of expectedIgnored) {
            const item = asObject(entry, `${name}.ignored[]`);
            expect(ignored.get(String(item.id)), `${name} ignore reason for id ${item.id}`).toBe(item.reason);
          }
        } else {
          expect(applied, `${name} should apply every frame`).toHaveLength(frames.length);
        }
        expect(state.terminated, `${name} should reach a terminal state`).toBe(true);
      });
    }
    expect(seen).toEqual(new Set(['stream-stable', 'stream-failed', 'stream-interrupted', 'stream-canceled', 'reconnect-gap-snapshot', 'late-duplicate-ignored']));
  });

  it('refines the SSE fixtures with scenario-specific expectations', () => {
    const byName = new Map(loadFixtures('sse').map((f) => [f.file.replace('sse/', '').replace('.json', ''), f.value]));

    const stable = replay(byName.get('stream-stable') as Record<string, unknown>);
    expect(stable.terminal?.kind).toBe('stable');
    expect(stable.terminal?.revision?.revisionNo).toBe(1);
    expect(stable.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物并释放氧气的过程。');
    expect(stable.reasoning).toBe('先明确光合作用的场所与原料。再说明光能如何被转化为化学能。');
    expect(stable.state).toBe('STREAMING_ANSWER');

    const failed = replay(byName.get('stream-failed') as Record<string, unknown>);
    expect(failed.terminal?.kind).toBe('failed');
    expect(failed.terminal?.errorCode).toBe('RATE_LIMITED');
    expect(failed.terminal?.params).toEqual({ httpStatus: 429 });
    expect(failed.answer).toBe('光合作用是植物利用光能');

    const interrupted = replay(byName.get('stream-interrupted') as Record<string, unknown>);
    expect(interrupted.terminal?.kind).toBe('interrupted');
    expect(interrupted.reasoning).toBe('先明确光合作用的场所。再说明能量转换。');

    const canceled = replay(byName.get('stream-canceled') as Record<string, unknown>);
    expect(canceled.terminal?.kind).toBe('canceled');
    expect(canceled.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物');

    const gap = replay(byName.get('reconnect-gap-snapshot') as Record<string, unknown>);
    expect(gap.appliedSeqs).toEqual([12, 13, 14]);
    expect(gap.gapsDetected).toBeGreaterThanOrEqual(1);
    expect(gap.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物并释放氧气的过程。');

    const late = replay(byName.get('late-duplicate-ignored') as Record<string, unknown>);
    expect(late.appliedSeqs).toEqual([1, 3, 4]);
    expect(late.terminal?.kind).toBe('stable');
  });
});

interface Replay {
  appliedSeqs: readonly number[];
  gapsDetected: number;
  answer: string;
  reasoning: string;
  state: string | null;
  terminal: { kind: string; revision?: { revisionNo?: number }; errorCode?: string; params?: unknown } | null;
}

function replay(fixture: Record<string, unknown>): Replay {
  const operationId = fixture.operationId as string;
  const lastEventId = typeof fixture.lastEventId === 'string' ? fixture.lastEventId : null;
  const frames = fixture.frames as Record<string, unknown>[];
  const state = new OperationEventState(operationId, {}, lastEventId);
  for (const frame of frames) {
    state.apply({
      id: typeof frame.id === 'string' ? frame.id : undefined,
      event: frame.event as string,
      data: frame.data,
    });
  }
  return {
    appliedSeqs: state.appliedSeqs,
    gapsDetected: state.gapsDetected,
    answer: state.answer,
    reasoning: state.reasoning,
    state: state.state,
    terminal: state.terminal as Replay['terminal'],
  };
}