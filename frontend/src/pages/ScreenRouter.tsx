import { useRoute } from "../hooks/useRoute";
import { HomePage } from "./HomePage";
import { ChatPage } from "./ChatPage";
import { LibraryPage } from "./LibraryPage";
import { SnapshotScreen } from "./SnapshotScreen";

/** 栈顶那屏 → 组件。精修过的三屏逐屏点名，其余按采集坐标还原；名单与夹具条目名一致。 */
export function ScreenRouter() {
  const route = useRoute();
  if (route.screen === "home") return <HomePage />;
  if (route.screen === "chat") return <ChatPage />;
  if (route.screen === "library") return <LibraryPage />;
  return <SnapshotScreen screen={route.screen} />;
}
