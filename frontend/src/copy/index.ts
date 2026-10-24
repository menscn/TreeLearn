import { zhCN } from "./zh-CN";

/** 当前界面语言；本期只有简中（规格 §3）。 */
export const interfaceLocale = "zh-CN";

const RESOURCES: Record<string, string> = zhCN;

/** 文案缺失时的大声失败：静默回退会把「这条文案没写」读成「文案就是这样」。 */
export class MissingCopyKey extends Error {
  constructor(key: string) {
    super(`文案表缺少 key：${key}（语言 ${interfaceLocale}）`);
    this.name = "MissingCopyKey";
  }
}

export type CopyKey = keyof typeof zhCN;

/** 按 key 取用户可见文案；生产代码不得出现第二处查表实现。 */
export function copyText(key: string): string {
  const text = RESOURCES[key];
  if (text === undefined) throw new MissingCopyKey(key);
  return text;
}

export function hasCopy(key: string): boolean {
  return Object.prototype.hasOwnProperty.call(RESOURCES, key);
}

/**
 * 带占位符文案的唯一格式化出口：沿用 Android `strings.xml` 的 `%1$s` / `%1$d` 约定，
 * 生产代码不得在组件里自己拼用户可见句子。
 */
export function copyFormat(key: string, ...args: (string | number)[]): string {
  let text = copyText(key);
  args.forEach((arg, index) => {
    const value = String(arg);
    text = text.split(`%${index + 1}$s`).join(value).split(`%${index + 1}$d`).join(value);
  });
  return text;
}

/** 排序后的键名，供守门与测试逐条点名，不依赖对象插入顺序。 */
export function copyKeys(): string[] {
  return Object.keys(RESOURCES).sort();
}
