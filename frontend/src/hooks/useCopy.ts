import { copyText } from "../copy";

/** 组件层取文案的唯一入口。返回的函数标识稳定，可安全放进依赖数组。 */
export function useCopy(): (key: string) => string {
  return copyText;
}
