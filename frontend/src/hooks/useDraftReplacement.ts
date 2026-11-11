import { draftFor } from "../store/workspace";
import { useCopy } from "./useCopy";

/** Ask before replacing an existing draft; empty drafts keep the direct action. */
export function useDraftReplacement(): (key: string) => boolean {
  const t = useCopy();
  return (key) => draftFor(key) === "" || window.confirm(t("draft_replace_confirm"));
}
