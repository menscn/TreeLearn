import { copyFormat } from "../copy";
import { useCopy } from "../hooks/useCopy";
import type { DesktopBranch } from "../platform/desktop";

export function desktopBranchLabels(branches: DesktopBranch[], rootLabel: string): Map<string, string> {
  const labels = new Map<string, string>();
  const seen = new Set<string>();
  let childNumber = 0;
  function visit(branch: DesktopBranch) {
    if (seen.has(branch.id)) return;
    seen.add(branch.id);
    labels.set(branch.id, branch.parentBranchId === null ? rootLabel : copyFormat("desktop_branch_child", ++childNumber));
    branches.filter((item) => item.parentBranchId === branch.id).forEach(visit);
  }
  branches.filter((branch) => branch.parentBranchId === null).forEach(visit);
  branches.forEach(visit);
  return labels;
}

export function DesktopBranches({ branches, active, disabled, onSelect }: {
  branches: DesktopBranch[];
  active: string;
  disabled: boolean;
  onSelect: (id: string) => void;
}) {
  const t = useCopy();
  const labels = desktopBranchLabels(branches, t("desktop_branch_root"));
  const ordered: { branch: DesktopBranch; depth: number }[] = [];
  const seen = new Set<string>();
  function visit(branch: DesktopBranch, depth: number) {
    if (seen.has(branch.id)) return;
    seen.add(branch.id);
    ordered.push({ branch, depth });
    branches.filter((item) => item.parentBranchId === branch.id).forEach((child) => visit(child, depth + 1));
  }
  branches.filter((branch) => branch.parentBranchId === null).forEach((branch) => visit(branch, 0));
  branches.forEach((branch) => visit(branch, 0));
  return <nav className="desktop-branches" aria-label={t("desktop_branch_navigation")}>
    <p className="desktop-branches-title">{t("desktop_branch_navigation")}</p>
    {ordered.map(({ branch, depth }) => <div className="desktop-branch-item" key={branch.id} style={{ paddingInlineStart: `${Math.min(depth, 8) * 16}px` }}>
      <button type="button" className="desktop-branch-link" data-branch-id={branch.id} aria-current={active === branch.id ? "page" : undefined} disabled={disabled} onClick={() => onSelect(branch.id)}>
        {labels.get(branch.id)}
      </button>
    </div>)}
  </nav>;
}
