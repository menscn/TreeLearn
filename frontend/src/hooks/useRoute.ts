import { useCallback, useSyncExternalStore } from "react";
import {
  canGoBack,
  currentScreen,
  goBack,
  navigate,
  replaceStack,
  routeSnapshot,
  subscribeRoute
} from "../store/screenRoute";

export function useRoute() {
  useSyncExternalStore(subscribeRoute, routeSnapshot, routeSnapshot);
  const open = useCallback((screen: string) => navigate(screen), []);
  const openTab = useCallback((screen: string) => replaceStack(screen), []);
  const back = useCallback(() => goBack(), []);
  return { screen: currentScreen(), canGoBack: canGoBack(), open, openTab, back };
}
