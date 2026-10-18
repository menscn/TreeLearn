import { useRoute } from "../hooks/useRoute";
import { HomePage } from "./HomePage";
import { ChatPage } from "./ChatPage";
import { LibraryPage } from "./LibraryPage";
import { FixtureListPage } from "./FixtureListPage";

/** 栈顶那屏 → 组件。精修过的逐屏点名，其余走通用读取；名单与夹具条目名一致。 */
export function ScreenRouter() {
  const route = useRoute();
  if (route.screen === "home") return <HomePage />;
  if (route.screen === "chat") return <ChatPage />;
  if (route.screen === "library") return <LibraryPage />;
  return <FixtureListPage screen={route.screen} />;
}
