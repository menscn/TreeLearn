import { useEffect, useRef, useState } from "react";
import { useCopy } from "../hooks/useCopy";
import type { TreeModel } from "../store/workspaceData";
import { IconButton } from "./IconButton";

interface TreeOverlayProps {
  tree: TreeModel;
  onClose: () => void;
}

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value * 10) / 10));
}

/** 知识树覆盖层：只画夹具能证明的根节点，子节点没采集就不补。 */
export function TreeOverlay({ tree, onClose }: TreeOverlayProps) {
  const t = useCopy();
  const [zoom, setZoom] = useState(1);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div
      className="tree-overlay"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="tree-dialog" role="dialog" aria-modal="true" aria-labelledby="tree-overlay-title">
        <header className="tree-header">
          <div className="tree-heading">
            <h2 id="tree-overlay-title">{tree.title}</h2>
            <p>{tree.subtitle}</p>
          </div>
          <IconButton
            action="close-tree"
            buttonRef={closeRef}
            glyph="chat_close"
            id="tree-close"
            label={t("chat_tree_close")}
            onClick={onClose}
          />
        </header>
        <div className="tree-canvas">
          <div className="tree-content" style={{ transform: `scale(${zoom})` }}>
            <div className="tree-node" data-tree-node="root">
              {tree.rootLabel}
            </div>
          </div>
        </div>
        <p className="tree-note">{t("chat_tree_only_root")}</p>
        <div className="tree-tools">
          <IconButton
            action="tree-zoom-out"
            glyph="zoom_out"
            label={t("tree_zoom_out")}
            onClick={() => setZoom((value) => clampZoom(value - ZOOM_STEP))}
          />
          <output className="tree-zoom" data-zoom role="status">
            {Math.round(zoom * 100)}%
          </output>
          <IconButton
            action="tree-zoom-in"
            glyph="zoom_in"
            label={t("tree_zoom_in")}
            onClick={() => setZoom((value) => clampZoom(value + ZOOM_STEP))}
          />
          <IconButton
            action="tree-fit"
            glyph="tree_fit"
            label={t("tree_fit")}
            onClick={() => setZoom(1)}
          />
        </div>
      </div>
    </div>
  );
}
