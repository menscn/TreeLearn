// 工作区布局偏好（任务 T-2026-10-10-031）：A 专注阅读、B 并排分支、C 树图（占位）。
// 属本地视图偏好，非权威数据：读取时校验取值，未知值回落 A；storage 不可用时静默降级为 A。
export const WORKSPACE_LAYOUTS = ['A', 'B', 'C'] as const;
export type WorkspaceLayout = (typeof WORKSPACE_LAYOUTS)[number];
export const LAYOUT_STORAGE_KEY = 'workspace:layout';

function isLayout(value: string | null): value is WorkspaceLayout {
  return value !== null && (WORKSPACE_LAYOUTS as readonly string[]).includes(value);
}

export function readLayout(): WorkspaceLayout {
  try {
    const value = window.localStorage.getItem(LAYOUT_STORAGE_KEY);
    return isLayout(value) ? value : 'A';
  } catch {
    return 'A';
  }
}

export function writeLayout(layout: WorkspaceLayout): void {
  try {
    window.localStorage.setItem(LAYOUT_STORAGE_KEY, layout);
  } catch {
    // 隐私模式/无 localStorage：仅本次会话生效，不阻塞交互。
  }
}