export interface DesktopMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: number;
  status: "pending" | "complete" | "failed" | "interrupted" | "cancelled";
  requestId?: string;
  persona?: string;
  branchId?: string;
  attachment?: { id: string; name: string; size: number } | null;
}

export interface DesktopAttachment { id: string; name: string; size: number; preview: string }

export interface DesktopBranch {
  id: string;
  topicId: string;
  parentBranchId: string | null;
  sourceMessageId: string | null;
  createdAt: number;
  messages: DesktopMessage[];
}

export interface DesktopTopic {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: DesktopMessage[];
  persona?: string;
  rootBranchId?: string;
  branches?: DesktopBranch[];
}

export interface DesktopState {
  settings: { baseUrl: string; model: string; hasApiKey: boolean; defaultPersona?: string };
  topics: DesktopTopic[];
  drafts: Record<string, string>;
  attachmentDrafts?: Record<string, DesktopAttachment>;
}

export interface DesktopApi {
  getState(): Promise<DesktopState>;
  saveSettings(input: { baseUrl: string; model: string; apiKey?: string }): Promise<void>;
  deleteApiKey(): Promise<void>;
  deleteTopic(input: { topicId: string }): Promise<void>;
  setPersona(input: { topicId?: string | null; persona: string }): Promise<void>;
  saveDraft(input: { topicId?: string | null; branchId?: string | null; text: string }): Promise<void>;
  sendMessage(input: { topicId?: string | null; branchId?: string | null; text: string; requestId: string; attachmentId?: string | null }): Promise<{ topicId: string; branchId?: string }>;
  selectAttachment?(input: { topicId?: string | null; branchId?: string | null }): Promise<{ cancelled: boolean; attachment?: DesktopAttachment }>;
  removeAttachment?(input: { topicId?: string | null; branchId?: string | null }): Promise<void>;
  createBranch(input: { topicId: string; parentBranchId: string; sourceMessageId: string; requestId: string }): Promise<{ branchId: string }>;
  retryMessage(input: { requestId: string }): Promise<{ topicId: string }>;
  cancelMessage(input: { requestId: string }): Promise<{ cancelled: boolean }>;
  onProgress(callback: (event: { requestId: string; topicId: string; branchId?: string; content: string }) => void): () => void;
  onBeforeClose(callback: () => boolean | Promise<boolean>): () => void;
  onCloseCancelled(callback: () => void): () => void;
}

declare global {
  interface Window { treelearnDesktop?: DesktopApi }
}
