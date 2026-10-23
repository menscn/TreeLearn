import { useRoute } from "../hooks/useRoute";
import { SnapshotScreen } from "./SnapshotScreen";

/**
 * 旧栈路由只保留采集坐标快照：主页、学习库与对话都已迁到工作区，
 * 这里继续供 parity 证据与尚未精修的采集屏使用，不复制工作区交互。
 */
export function ScreenRouter() {
  const route = useRoute();
  return <SnapshotScreen screen={route.screen} />;
}
